package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.kayan.ConfigFormat
import io.kayan.KayanValidationMode
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * Shared root-level Kayan config exposed as `kayanRoot { ... }`.
 *
 * Child projects can inherit these conventions through `kayan { inheritFromRoot() }`
 * and opt into a subset of the root schema via `schema { include(...) }`.
 */
public abstract class KayanRootExtension {
    internal val schemaBuilder: KayanSchemaBuilder = KayanSchemaBuilder()

    /** Shared flavor name used when child modules do not override it locally. */
    public abstract val flavor: Property<String>

    /** Shared base config file that declares defaults and per-flavor values. */
    public abstract val baseConfigFile: RegularFileProperty

    /** Optional shared override config file layered on top of the base config. */
    public abstract val customConfigFile: RegularFileProperty

    /** Shared config source format, or `AUTO` to infer it from file extensions. */
    public abstract val configFormat: Property<ConfigFormat>

    /** Shared validation mode used when child modules do not override it locally. */
    public abstract val validationMode: Property<KayanValidationMode>

    /** Configures the shared root schema using a Gradle [Action]. */
    public fun schema(action: Action<in KayanSchemaBuilder>) {
        action.execute(schemaBuilder)
    }

    /** Configures the shared root schema using the Kotlin DSL. */
    public fun schema(action: KayanSchemaBuilder.() -> Unit) {
        schemaBuilder.action()
    }

    internal fun serializedSchemaEntries(): List<String> =
        serializedSchemaEntriesEither().getOrElse { throw it.toGradleException() }

    @Suppress("ReturnCount")
    internal fun serializedSchemaEntriesEither(): Either<PluginConfigurationError, List<String>> {
        if (schemaBuilder.hasRootIncludes()) {
            return PluginConfigurationError.RootSchemaIncludesNotSupported.left()
        }
        if (!schemaBuilder.hasLocalEntries()) {
            return PluginConfigurationError.MissingRootSchemaEntries.left()
        }

        return schemaBuilder.serializedLocalEntries().right()
    }
}
