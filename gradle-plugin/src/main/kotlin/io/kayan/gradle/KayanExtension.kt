package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.kayan.ConfigFormat
import io.kayan.KayanValidationMode
import io.kayan.closeKeyMatches
import org.gradle.api.Action
import org.gradle.api.Project
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
@Suppress("TooManyFunctions")
public abstract class KayanExtension {
    internal val schemaBuilder: KayanSchemaBuilder = KayanSchemaBuilder()
    private val resolvedBuildValueProviders:
        MutableMap<BuildValueRequest, Provider<ResolvedBuildValue>> =
        mutableMapOf()
    private var inheritsFromRoot: Boolean = false

    internal lateinit var owningProject: Project

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

    /** How strictly shared config files should be validated against this module's schema. */
    public abstract val validationMode: Property<KayanValidationMode>

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

    /**
     * Inherits shared config defaults and schema ownership from `kayanRoot { ... }`
     * defined on this build's root project.
     */
    public fun inheritFromRoot() {
        if (inheritsFromRoot) {
            return
        }
        val rootExtension = rootExtensionEither().getOrElse { throw it.toGradleException() }
        if (schemaBuilder.hasLocalEntries()) {
            throw PluginConfigurationError.LocalSchemaEntriesNotSupportedWithRootInheritance.toGradleException()
        }

        inheritsFromRoot = true
        schemaBuilder.enableIncludeOnlyMode()
        flavor.convention(rootExtension.flavor)
        baseConfigFile.convention(rootExtension.baseConfigFile)
        customConfigFile.convention(rootExtension.customConfigFile)
        configFormat.convention(rootExtension.configFormat)
        validationMode.convention(rootExtension.validationMode)
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

    /**
     * Configures target-specific KMP source generation using the Kotlin DSL.
     *
     * This is an ergonomic alias for [targetSourceSets] that keeps the DSL
     * focused on target-oriented mappings such as `targets { ios(); jvm() }`.
     *
     * @see targetSourceSets
     */
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
    public fun buildValue(jsonKey: String): KayanBuildValue = buildValue(jsonKey = jsonKey, targetName = null)

    /**
     * Exposes a schema entry to Gradle build logic through typed accessors for one explicit target.
     *
     * When [targetName] is provided, target overlays participate in the same
     * precedence used for generated KMP target sources.
     */
    @ExperimentalKayanGradleApi
    public fun buildValue(jsonKey: String, targetName: String?): KayanBuildValue {
        val schema = requireSchema(serializedSchemaEntries())
        validateSchemaKeyEither(schema, jsonKey).getOrElse { throw it.toGradleException() }

        return KayanBuildValue(
            valueProvider = resolvedBuildValueProvider(jsonKey, targetName),
        )
    }

    internal fun serializedSchemaEntries(): List<String> =
        serializedSchemaEntriesEither().getOrElse { throw it.toGradleException() }

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

    @OptIn(ExperimentalKayanGenerationApi::class)
    internal fun whenTargetSourceSetMappingAdded(action: (KayanTargetSourceSetMapping) -> Unit) {
        targetSourceSetContainer.whenEntryAdded(action)
    }

    private fun resolvedBuildValueProvider(jsonKey: String, targetName: String?): Provider<ResolvedBuildValue> {
        val normalizedTargetName = targetName?.let { requireConfigured(it, "targetName") }
        val request = BuildValueRequest(jsonKey = jsonKey, targetName = normalizedTargetName)

        return resolvedBuildValueProviders.getOrPut(request) {
            providers.of(KayanConfigValueSource::class.java) { spec ->
                spec.parameters.baseConfigFile.set(baseConfigFile)
                spec.parameters.customConfigFile.set(customConfigFile)
                spec.parameters.configFormat.set(configFormat)
                spec.parameters.validationMode.set(validationMode)
                spec.parameters.flavor.set(flavor)
                spec.parameters.schemaEntries.set(serializedSchemaEntries())
                spec.parameters.jsonKey.set(jsonKey)
                normalizedTargetName?.let(spec.parameters.targetName::set)
            }.map { serialized ->
                deserializeResolvedBuildValueEither(jsonKey, serialized)
                    .getOrElse { throw it.toGradleException() }
            }
        }
    }

    @Suppress("ReturnCount")
    private fun serializedSchemaEntriesEither(): Either<PluginConfigurationError, List<String>> {
        if (!inheritsFromRoot) {
            if (schemaBuilder.hasRootIncludes()) {
                return PluginConfigurationError.RootSchemaInclusionRequiresInheritance.left()
            }

            return schemaBuilder.serializedLocalEntries().right()
        }

        val rootExtension = rootExtensionEither().getOrElse { return it.left() }
        val rootEntries = deserializeInheritedRootEntriesEither(
            rootExtension.serializedSchemaEntriesEither().getOrElse { return it.left() },
        ).getOrElse { return it.left() }
        val rootEntriesByJsonKey = rootEntries.associateBy(KayanSchemaEntrySpec::jsonKey)

        if (schemaBuilder.includesAll()) {
            return rootEntries.map(KayanSchemaEntrySpec::serialize).right()
        }

        val includedKeys = schemaBuilder.includedJsonKeys()
        if (includedKeys.isEmpty()) {
            return PluginConfigurationError.MissingInheritedSchemaSelection.left()
        }

        includedKeys.forEach { jsonKey ->
            if (rootEntriesByJsonKey[jsonKey] == null) {
                val suggestions = closeKeyMatches(jsonKey, rootEntriesByJsonKey.keys.toList())
                return PluginConfigurationError.UnknownInheritedSchemaKey(jsonKey, suggestions).left()
            }
        }

        return rootEntries
            .filter { entry -> includedKeys.contains(entry.jsonKey) }
            .map(KayanSchemaEntrySpec::serialize)
            .right()
    }

    private fun rootExtensionEither(): Either<PluginConfigurationError, KayanRootExtension> =
        owningProject.rootProject.extensions.findByType(KayanRootExtension::class.java)?.right()
            ?: PluginConfigurationError.MissingRootKayanConfiguration.left()
}

private data class BuildValueRequest(val jsonKey: String, val targetName: String?)
