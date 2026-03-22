package io.kayan.gradle

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import io.kayan.ConfigDefinition
import io.kayan.ConfigSchema
import io.kayan.ConfigValueKind
import io.kayan.SchemaError
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
import org.gradle.api.provider.Property

public abstract class KayanExtension {
    internal val schemaBuilder: KayanSchemaBuilder = KayanSchemaBuilder()

    public abstract val packageName: Property<String>
    public abstract val flavor: Property<String>
    public abstract val baseConfigFile: RegularFileProperty
    public abstract val customConfigFile: RegularFileProperty
    public abstract val className: Property<String>
    public abstract val jsonSchemaOutputFile: RegularFileProperty
    public abstract val markdownSchemaOutputFile: RegularFileProperty

    public fun schema(action: Action<in KayanSchemaBuilder>) {
        action.execute(schemaBuilder)
    }

    public fun schema(action: KayanSchemaBuilder.() -> Unit) {
        schemaBuilder.action()
    }

    internal fun serializedSchemaEntries(): List<String> = schemaBuilder.entries.map(KayanSchemaEntrySpec::serialize)
}

public class KayanSchemaBuilder internal constructor() {
    internal val entries: MutableList<KayanSchemaEntrySpec> = mutableListOf()

    public fun string(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING,
            required = required,
            nullable = nullable,
        )
    }

    public fun boolean(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.BOOLEAN,
            required = required,
            nullable = nullable,
        )
    }

    public fun int(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.INT,
            required = required,
            nullable = nullable,
        )
    }

    public fun long(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.LONG,
            required = required,
            nullable = nullable,
        )
    }

    public fun double(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.DOUBLE,
            required = required,
            nullable = nullable,
        )
    }

    public fun stringMap(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING_MAP,
            required = required,
            nullable = nullable,
        )
    }

    public fun stringList(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING_LIST,
            required = required,
            nullable = nullable,
        )
    }

    public fun stringListMap(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.STRING_LIST_MAP,
            required = required,
            nullable = nullable,
        )
    }

    public fun enumValue(
        jsonKey: String,
        propertyName: String,
        enumTypeName: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = ConfigValueKind.ENUM,
            required = required,
            nullable = nullable,
            enumTypeName = enumTypeName,
        )
    }

    public fun enum(
        jsonKey: String,
        propertyName: String,
        enumTypeName: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        enumValue(
            jsonKey = jsonKey,
            propertyName = propertyName,
            enumTypeName = enumTypeName,
            required = required,
            nullable = nullable,
        )
    }

    public fun custom(
        jsonKey: String,
        propertyName: String,
        rawKind: ConfigValueKind,
        adapter: String,
        required: Boolean = false,
        nullable: Boolean = false,
    ) {
        add(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = rawKind,
            required = required,
            nullable = nullable,
            adapterClassName = adapter,
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
    ) {
        entries += KayanSchemaEntrySpec(
            jsonKey = jsonKey,
            propertyName = propertyName,
            kind = kind,
            required = required,
            nullable = nullable,
            enumTypeName = enumTypeName,
            adapterClassName = adapterClassName,
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

        private fun JsonObject.requireBoolean(
            key: String,
            entryIndex: Int,
        ): Either<SchemaError, Boolean> =
            ((get(key) as? JsonPrimitive)?.booleanOrNull)?.right()
                ?: SchemaError.MissingRequiredField(entryIndex, key).left()
    }
}
