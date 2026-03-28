package io.kayan.gradle

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import io.kayan.ConfigDefinition
import io.kayan.ConfigFormat
import io.kayan.ConfigSchema
import io.kayan.ConfigValueKind
import io.kayan.SchemaError
import io.kayan.closeKeyMatches
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Top-level DSL exposed as `kayan { ... }`.
 *
 * This extension defines the schema, input files, generated type name, schema
 * export locations, and optional Android-specific generation behavior used by
 * Kayan's Gradle tasks.
 */
public abstract class KayanExtension {
    internal val schemaBuilder: KayanSchemaBuilder = KayanSchemaBuilder()
    private val resolvedBuildValueProviders: MutableMap<String, Provider<ResolvedBuildValue>> = mutableMapOf()

    @get:Inject
    internal abstract val objects: ObjectFactory

    @get:Inject
    internal abstract val providers: ProviderFactory

    @OptIn(ExperimentalKayanGenerationApi::class)
    private val androidFlavorSourceSetSpec: KayanAndroidFlavorSourceSetSpec by lazy {
        objects.newInstance(KayanAndroidFlavorSourceSetSpec::class.java).apply {
            flavors.convention(emptyList())
        }
    }

    @OptIn(ExperimentalKayanGenerationApi::class)
    private val targetSourceSetContainer: KayanTargetSourceSetContainer by lazy {
        objects.newInstance(KayanTargetSourceSetContainer::class.java)
    }

    /** Package name for the generated Kotlin type and related schema metadata. */
    public abstract val packageName: Property<String>

    /** Flavor name to resolve for non-Android source generation. */
    public abstract val flavor: Property<String>

    /** Base config file that declares defaults and per-flavor values. */
    public abstract val baseConfigFile: RegularFileProperty

    /** Optional override config file layered on top of the base config. */
    public abstract val customConfigFile: RegularFileProperty

    /** Config source format, or `AUTO` to infer it from file extensions. */
    public abstract val configFormat: Property<ConfigFormat>

    /** Name of the generated Kotlin object or type. */
    public abstract val className: Property<String>

    /** Output location for the generated JSON Schema file. */
    public abstract val jsonSchemaOutputFile: RegularFileProperty

    /** Output location for the generated Markdown schema reference. */
    public abstract val markdownSchemaOutputFile: RegularFileProperty

    /** Configures the schema that drives validation, generation, and schema export using a Gradle [Action]. */
    public fun schema(action: Action<in KayanSchemaBuilder>) {
        action.execute(schemaBuilder)
    }

    /** Configures the schema that drives validation, generation, and schema export using the Kotlin DSL. */
    public fun schema(action: KayanSchemaBuilder.() -> Unit) {
        schemaBuilder.action()
    }

    /** Configures Android flavor-specific generation using a Gradle [Action]. */
    @ExperimentalKayanGenerationApi
    public fun androidFlavorSourceSets(action: Action<in KayanAndroidFlavorSourceSetSpec>) {
        action.execute(androidFlavorSourceSetSpec)
    }

    /** Configures Android flavor-specific generation using the Kotlin DSL. */
    @ExperimentalKayanGenerationApi
    public fun androidFlavorSourceSets(action: KayanAndroidFlavorSourceSetSpec.() -> Unit) {
        androidFlavorSourceSetSpec.action()
    }

    /** Configures target-specific KMP source generation using a Gradle [Action]. */
    @ExperimentalKayanGenerationApi
    public fun targetSourceSets(action: Action<in KayanTargetSourceSetContainer>) {
        action.execute(targetSourceSetContainer)
    }

    /** Configures target-specific KMP source generation using the Kotlin DSL. */
    @ExperimentalKayanGenerationApi
    public fun targetSourceSets(action: KayanTargetSourceSetContainer.() -> Unit) {
        targetSourceSetContainer.action()
    }

    /**
     * Configures conventional target-specific KMP source generation.
     *
     * Supported values are `android`, `ios`, `jvm`, `js`, and `wasmJs`.
     */
    @ExperimentalKayanGenerationApi
    public fun targets(vararg targetNames: String) {
        targetSourceSetContainer.targets(*targetNames)
    }

    /** Configures target-specific KMP source generation using the Kotlin DSL. */
    @ExperimentalKayanGenerationApi
    public fun targets(action: KayanTargetSourceSetContainer.() -> Unit) {
        targetSourceSetContainer.action()
    }

    /**
     * Exposes a schema entry to Gradle build logic through typed accessors.
     *
     * The key must already exist in the configured schema. Type validation is
     * deferred to the accessor calls on [KayanBuildValue].
     */
    @ExperimentalKayanGradleApi
    public fun buildValue(jsonKey: String): KayanBuildValue {
        val schema = requireSchema(serializedSchemaEntries())
        validateSchemaKeyEither(schema, jsonKey).getOrElse { throw it.toGradleException() }

        return KayanBuildValue(
            valueProvider = resolvedBuildValueProvider(jsonKey),
        )
    }

    internal fun serializedSchemaEntries(): List<String> = schemaBuilder.entries.map(KayanSchemaEntrySpec::serialize)

    @OptIn(ExperimentalKayanGenerationApi::class)
    internal fun androidFlavorSourceSetFlavors(): List<String> = androidFlavorSourceSetSpec.flavors.getOrElse(
        emptyList(),
    )

    @OptIn(ExperimentalKayanGenerationApi::class)
    internal fun targetSourceSetMappings(): List<KayanTargetSourceSetMapping> =
        targetSourceSetContainer.entries.map { entry ->
            KayanTargetSourceSetMapping(
                sourceSetName = entry.sourceSetName.orNull.orEmpty(),
                targetName = entry.targetName.orNull.orEmpty(),
            )
        }

    private fun resolvedBuildValueProvider(jsonKey: String): Provider<ResolvedBuildValue> =
        resolvedBuildValueProviders.getOrPut(jsonKey) {
            providers.of(KayanConfigValueSource::class.java) { spec ->
                spec.parameters.baseConfigFile.set(baseConfigFile)
                spec.parameters.customConfigFile.set(customConfigFile)
                spec.parameters.configFormat.set(configFormat)
                spec.parameters.flavor.set(flavor)
                spec.parameters.schemaEntries.set(serializedSchemaEntries())
                spec.parameters.jsonKey.set(jsonKey)
            }.map { serialized ->
                deserializeResolvedBuildValueEither(jsonKey, serialized)
                    .getOrElse { throw it.toGradleException() }
            }
        }

    private fun validateSchemaKeyEither(
        schema: ConfigSchema,
        jsonKey: String,
    ): Either<BuildTimeAccessError, Unit> {
        if (schema.definitionFor(jsonKey) != null) {
            return Unit.right()
        }

        val suggestions = closeKeyMatches(jsonKey, schema.entries.map(ConfigDefinition::jsonKey))
        return BuildTimeAccessError.UnknownSchemaKey(jsonKey, suggestions).left()
    }
}

/**
 * Builder used inside `kayan { schema { ... } }` to declare config entries.
 *
 * Each function maps a source key to a generated Kotlin property. The shared
 * flags behave the same across entry types: `required` forces every final
 * flavor result to contain a value, `nullable` allows explicit `null`, and
 * `preventOverride` blocks the custom override file from replacing the base
 * value.
 */
public class KayanSchemaBuilder internal constructor() {
    internal val entries: MutableList<KayanSchemaEntrySpec> = mutableListOf()

    /** Adds a string schema entry. */
    public fun string(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a boolean schema entry. */
    public fun boolean(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.BOOLEAN,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds an integer schema entry. */
    public fun int(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.INT,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a long schema entry. */
    public fun long(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.LONG,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a double schema entry. */
    public fun double(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.DOUBLE,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a string-to-string map schema entry. */
    public fun stringMap(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING_MAP,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a string list schema entry. */
    public fun stringList(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING_LIST,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a string-to-string-list map schema entry. */
    public fun stringListMap(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING_LIST_MAP,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds an enum-backed schema entry using [enumTypeName] as the generated enum type name. */
    public fun enumValue(
        jsonKey: String,
        propertyName: String,
        enumTypeName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.ENUM,
            required = required,
            nullable = nullable,
            enumTypeName = enumTypeName,
            preventOverride = preventOverride,
        )
    }

    /** Alias for [enumValue] for DSL ergonomics. */
    public fun enum(
        jsonKey: String,
        propertyName: String,
        enumTypeName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        enumValue(
            jsonKey = jsonKey,
            propertyName = propertyName,
            enumTypeName = enumTypeName,
            required = required,
            nullable = nullable,
            preventOverride = preventOverride,
        )
    }

    /** Adds a custom schema entry rendered through the adapter identified by [adapter]. */
    public fun custom(
        jsonKey: String,
        propertyName: String,
        rawKind: ConfigValueKind,
        adapter: String,
        required: Boolean = false,
        nullable: Boolean = false,
        preventOverride: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = rawKind,
            required = required,
            nullable = nullable,
            adapterClassName = adapter,
            preventOverride = preventOverride,
        )
    }

    @Suppress("LongParameterList")
    private fun add(
        jsonKey: String,
        propertyName: String,
        kind: ConfigValueKind,
        required: Boolean,
        nullable: Boolean,
        enumTypeName: String? = null,
        adapterClassName: String? = null,
        preventOverride: Boolean = false,
    ) {
        entries += KayanSchemaEntrySpec(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = kind,
            required = required,
            nullable = nullable,
            enumTypeName = enumTypeName,
            adapterClassName = adapterClassName,
            preventOverride = preventOverride,
        )
    }
}

internal data class KayanSchemaEntrySpec(
    val jsonKey: String,
    val propertyName: String,
    val kind: ConfigValueKind,
    val required: Boolean,
    val nullable: Boolean,
    val enumTypeName: String? = null,
    val adapterClassName: String? = null,
    val preventOverride: Boolean = false,
) {
    fun serialize(): String = json.encodeToString(
        JsonElement.serializer(),
        buildJsonObject {
            put("jsonKey", JsonPrimitive(jsonKey))
            put("propertyName", JsonPrimitive(propertyName))
            put("kind", JsonPrimitive(kind.name))
            put("required", JsonPrimitive(required))
            put("nullable", JsonPrimitive(nullable))
            enumTypeName?.let { put("enumTypeName", JsonPrimitive(it)) }
            adapterClassName?.let { put("adapterClassName", JsonPrimitive(it)) }
            put("preventOverride", JsonPrimitive(preventOverride))
        },
    )

    fun toConfigDefinition(): ConfigDefinition = ConfigDefinition(
        jsonKey = jsonKey,
        propertyName = propertyName,
        kind = kind,
        required = required,
        nullable = nullable,
        enumTypeName = enumTypeName,
        adapterClassName = adapterClassName,
        preventOverride = preventOverride,
    )

    companion object {
        private val json: Json = Json { ignoreUnknownKeys = false }

        fun deserialize(serialized: String): KayanSchemaEntrySpec =
            deserializeEither(serialized = serialized, entryIndex = 0)
                .getOrElse { error(it.message()) }

        fun toSchema(serializedEntries: List<String>): ConfigSchema =
            toSchemaEither(serializedEntries)
                .getOrElse { errors -> error(errors.first().message()) }

        internal fun deserializeEither(
            serialized: String,
            entryIndex: Int,
        ): Either<SchemaError, KayanSchemaEntrySpec> = either {
            val root = try {
                json.parseToJsonElement(serialized)
            } catch (error: SerializationException) {
                raise(
                    SchemaError.InvalidSerializedJson(
                        entryIndex = entryIndex,
                        detail = error.message,
                        cause = error,
                    ),
                )
            }

            val jsonObject = root as? JsonObject ?: raise(SchemaError.InvalidSerializedRoot(entryIndex))
            val kindValue = jsonObject.requireString("kind", entryIndex).bind()
            val kind = try {
                ConfigValueKind.valueOf(kindValue)
            } catch (error: IllegalArgumentException) {
                raise(
                    SchemaError.InvalidEnumKind(
                        entryIndex = entryIndex,
                        rawValue = kindValue,
                        cause = error,
                    ),
                )
            }

            KayanSchemaEntrySpec(
                jsonKey = jsonObject.requireString("jsonKey", entryIndex).bind(),
                propertyName = jsonObject.requireString("propertyName", entryIndex).bind(),
                kind = kind,
                required = jsonObject.requireBoolean("required", entryIndex).bind(),
                nullable = jsonObject.requireBoolean("nullable", entryIndex).bind(),
                enumTypeName = jsonObject.optionalString("enumTypeName"),
                adapterClassName = jsonObject.optionalString("adapterClassName"),
                preventOverride = jsonObject.optionalBoolean("preventOverride") ?: false,
            )
        }

        @Suppress("ReturnCount")
        internal fun toSchemaEither(
            serializedEntries: List<String>,
        ): Either<NonEmptyList<SchemaError>, ConfigSchema> {
            val specErrors = mutableListOf<SchemaError>()
            val specs = mutableListOf<KayanSchemaEntrySpec>()

            serializedEntries.forEachIndexed { index, serialized ->
                when (val decoded = deserializeEither(serialized, index)) {
                    is Either.Left -> specErrors += decoded.value
                    is Either.Right -> specs += decoded.value
                }
            }

            specs.forEachIndexed { index, spec ->
                specErrors += validateSpec(index, spec)
            }
            specErrors += validateSchema(specs)

            specErrors.toNonEmptyListOrNull()?.let { return it.left() }

            val definitions = mutableListOf<ConfigDefinition>()
            val definitionErrors = mutableListOf<SchemaError>()
            specs.forEach { spec ->
                when (val definition = spec.toConfigDefinitionEither()) {
                    is Either.Left -> definitionErrors += definition.value
                    is Either.Right -> definitions += definition.value
                }
            }
            definitionErrors.toNonEmptyListOrNull()?.let { return it.left() }

            return try {
                ConfigSchema(definitions).right()
            } catch (error: IllegalArgumentException) {
                listOf(SchemaError.UnexpectedSchemaValidation(error)).toNonEmptyListOrNull()!!.left()
            }
        }

        private fun validateSpec(
            entryIndex: Int,
            spec: KayanSchemaEntrySpec,
        ): List<SchemaError> = buildList {
            if (spec.jsonKey.isBlank()) {
                add(SchemaError.BlankJsonKey(entryIndex))
            }
            if (spec.propertyName.isBlank()) {
                add(SchemaError.BlankPropertyName(entryIndex))
            }
            if (spec.required && spec.nullable) {
                add(SchemaError.RequiredAndNullable(entryIndex, spec.jsonKey))
            }
            if (spec.kind != ConfigValueKind.ENUM && spec.enumTypeName != null) {
                add(SchemaError.EnumTypeOnNonEnum(entryIndex, spec.jsonKey))
            }
            if (spec.kind == ConfigValueKind.ENUM && spec.enumTypeName.isNullOrBlank()) {
                add(SchemaError.MissingEnumType(entryIndex, spec.jsonKey))
            }
            if (spec.adapterClassName != null && spec.adapterClassName.isBlank()) {
                add(SchemaError.BlankAdapterClassName(entryIndex))
            }
        }

        private fun validateSchema(
            specs: List<KayanSchemaEntrySpec>,
        ): List<SchemaError> = buildList {
            if (specs.isEmpty()) {
                add(SchemaError.EmptySchema)
            }
            duplicateValues(specs.map(KayanSchemaEntrySpec::jsonKey))
                .takeIf { it.isNotEmpty() }
                ?.let { duplicates ->
                    add(SchemaError.DuplicateJsonKeys(duplicates))
                }
            duplicateValues(specs.map(KayanSchemaEntrySpec::propertyName))
                .takeIf { it.isNotEmpty() }
                ?.let { duplicates ->
                    add(SchemaError.DuplicatePropertyNames(duplicates))
                }
        }

        private fun duplicateValues(values: List<String>): List<String> {
            val counts = mutableMapOf<String, Int>()
            values.forEach { value ->
                counts[value] = counts.getOrDefault(value, 0) + 1
            }

            return counts
                .filterValues { it > 1 }
                .keys
                .toList()
        }

        private fun KayanSchemaEntrySpec.toConfigDefinitionEither(): Either<SchemaError, ConfigDefinition> =
            try {
                toConfigDefinition().right()
            } catch (error: IllegalArgumentException) {
                SchemaError.UnexpectedDefinitionValidation(error).left()
            }

        private fun JsonObject.requireString(
            key: String,
            entryIndex: Int,
        ): Either<SchemaError, String> =
            ((get(key) as? JsonPrimitive)?.content)?.right()
                ?: SchemaError.MissingRequiredField(entryIndex, key).left()

        private fun JsonObject.optionalString(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.optionalBoolean(key: String): Boolean? = (get(key) as? JsonPrimitive)?.booleanOrNull

        private fun JsonObject.requireBoolean(
            key: String,
            entryIndex: Int,
        ): Either<SchemaError, Boolean> =
            ((get(key) as? JsonPrimitive)?.booleanOrNull)?.right()
                ?: SchemaError.MissingRequiredField(entryIndex, key).left()
    }
}
