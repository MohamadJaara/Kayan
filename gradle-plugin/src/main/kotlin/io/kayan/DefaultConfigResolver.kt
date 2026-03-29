@file:OptIn(ExperimentalKayanApi::class)

package io.kayan

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.kayan.gradle.ExperimentalKayanGradleApi

@Suppress("TooManyFunctions")
/**
 * Default JVM implementation of [ConfigResolver].
 *
 * It is useful for tooling, tests, or non-Gradle integrations that want the
 * same parsing and layering behavior as the Gradle plugin. The no-arg
 * constructor assumes JSON input; the format-aware constructor is intended for
 * callers that already know whether the source is JSON or YAML.
 */
public class DefaultConfigResolver : ConfigResolver {
    private val parser: ConfigFormatParser

    /** Creates a resolver that parses JSON input. */
    public constructor() {
        parser = JsonConfigFormatParser()
    }

    /**
     * Creates a resolver for an explicit non-`AUTO` [configFormat].
     *
     * `AUTO` is not accepted here because there is no source file name available
     * to infer the format from.
     */
    @OptIn(ExperimentalKayanGradleApi::class)
    public constructor(configFormat: ConfigFormat) {
        require(configFormat != ConfigFormat.AUTO) {
            "DefaultConfigResolver requires JSON or YAML when constructed with an explicit ConfigFormat."
        }
        parser = parserFor(configFormat)
    }

    internal constructor(parser: ConfigFormatParser) {
        this.parser = parser
    }

    override fun parse(
        configJson: String,
        schema: ConfigSchema,
    ): AppConfigFile = parseEither(
        configJson = configJson,
        schema = schema,
        sourceName = DEFAULT_PARSE_SOURCE_NAME,
    ).getOrElse { throw it.toConfigValidationException() }

    /**
     * Parses [configJson] and reports diagnostics against the supplied [sourceName].
     *
     * Use this overload when error messages should mention a real file name or a
     * domain-specific label instead of the default placeholder source name.
     */
    public fun parse(
        configJson: String,
        schema: ConfigSchema,
        sourceName: String,
    ): AppConfigFile = parseEither(
        configJson = configJson,
        schema = schema,
        sourceName = sourceName,
    ).getOrElse { throw it.toConfigValidationException() }

    override fun resolve(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String?,
    ): ResolvedConfigsByFlavor = resolveEither(
        defaultConfigJson = defaultConfigJson,
        schema = schema,
        customConfigJson = customConfigJson,
        defaultConfigSourceName = DEFAULT_BASE_SOURCE_NAME,
        customConfigSourceName = DEFAULT_CUSTOM_SOURCE_NAME,
    ).getOrElse { throw it.toConfigValidationException() }

    /**
     * Resolves [defaultConfigJson] and [customConfigJson] while preserving custom source names in diagnostics.
     *
     * Use this overload when validation errors should point back to real file
     * names or human-readable source labels.
     */
    public fun resolve(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String?,
        defaultConfigSourceName: String,
        customConfigSourceName: String = DEFAULT_CUSTOM_SOURCE_NAME,
    ): ResolvedConfigsByFlavor = resolveEither(
        defaultConfigJson = defaultConfigJson,
        schema = schema,
        customConfigJson = customConfigJson,
        defaultConfigSourceName = defaultConfigSourceName,
        customConfigSourceName = customConfigSourceName,
    ).getOrElse { throw it.toConfigValidationException() }

    /**
     * Resolves [defaultConfigJson] and [customConfigJson] for one specific [targetName].
     *
     * Target overlays participate in precedence only when this overload is used.
     */
    @ExperimentalKayanApi
    public fun resolve(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String?,
        targetName: String,
        defaultConfigSourceName: String,
        customConfigSourceName: String = DEFAULT_CUSTOM_SOURCE_NAME,
    ): ResolvedConfigsByFlavor = resolveEither(
        defaultConfigJson = defaultConfigJson,
        schema = schema,
        customConfigJson = customConfigJson,
        defaultConfigSourceName = defaultConfigSourceName,
        customConfigSourceName = customConfigSourceName,
        targetName = targetName,
    ).getOrElse { throw it.toConfigValidationException() }

    internal fun parseEither(
        configJson: String,
        schema: ConfigSchema,
        sourceName: String,
    ): Either<ConfigError, AppConfigFile> = parseInternalEither(
        configJson = configJson,
        schema = schema,
        sourceName = sourceName,
    )

    internal fun resolveEither(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String?,
        defaultConfigSourceName: String,
        customConfigSourceName: String = DEFAULT_CUSTOM_SOURCE_NAME,
        targetName: String? = null,
    ): Either<ConfigError, ResolvedConfigsByFlavor> = resolveInternalEither(
        defaultConfigJson = defaultConfigJson,
        schema = schema,
        customConfigJson = customConfigJson,
        defaultConfigSourceName = defaultConfigSourceName,
        customConfigSourceName = customConfigSourceName,
        targetName = targetName,
    )

    private fun parseInternalEither(
        configJson: String,
        schema: ConfigSchema,
        sourceName: String,
    ): Either<ConfigError, AppConfigFile> = ConfigSectionParser(parser).parseEither(
        configJson = configJson,
        schema = schema,
        sourceName = sourceName,
    )

    private fun resolveInternalEither(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String?,
        defaultConfigSourceName: String,
        customConfigSourceName: String,
        targetName: String?,
    ): Either<ConfigError, ResolvedConfigsByFlavor> = either {
        val defaultConfig = parseInternalEither(defaultConfigJson, schema, defaultConfigSourceName).bind()
        val customConfig = customConfigJson?.let {
            parseInternalEither(it, schema, customConfigSourceName).bind()
        }
        CustomConfigValidator.validateEither(
            defaultConfig = defaultConfig,
            customConfig = customConfig,
            defaultConfigSourceName = defaultConfigSourceName,
            customConfigSourceName = customConfigSourceName,
        ).bind()

        ResolvedConfigsByFlavor(
            FlavorResolver.resolveFlavorsEither(
                schema = schema,
                defaultConfig = defaultConfig,
                customConfig = customConfig,
                targetName = targetName,
            ).bind(),
        )
    }

    internal companion object {
        internal const val FLAVORS_KEY: String = "flavors"
        internal const val TARGETS_KEY: String = "targets"
        internal const val RESOLVED_CONFIG_SOURCE_NAME: String = "resolved config"
        private const val DEFAULT_PARSE_SOURCE_NAME: String = "config"
        private const val DEFAULT_BASE_SOURCE_NAME: String = "default config"
        private const val DEFAULT_CUSTOM_SOURCE_NAME: String = "custom config"
        internal val IDENTIFIER_SEGMENT: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
