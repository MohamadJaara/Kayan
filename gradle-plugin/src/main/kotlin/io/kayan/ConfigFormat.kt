package io.kayan

import arrow.core.Either
import arrow.core.left
import arrow.core.right

/**
 * Selects how Kayan should parse the base config file and optional override file.
 *
 * In Gradle builds, [AUTO] is usually the most convenient choice because Kayan
 * can infer the parser from each file's extension. When both base and custom
 * files are present, they still need to resolve to the same concrete format.
 */
public enum class ConfigFormat {
    /** Reads the config source as JSON. */
    JSON,

    /** Reads the config source as YAML. */
    YAML,

    /** Detects the format from the source file extension such as `.json` or `.yaml`. */
    AUTO,
}

internal fun parserFor(format: ConfigFormat): ConfigFormatParser = when (format) {
    ConfigFormat.JSON -> JsonConfigFormatParser()
    ConfigFormat.YAML -> YamlConfigFormatParser()
    ConfigFormat.AUTO -> error("AUTO must be resolved before selecting a parser.")
}

internal fun inferConfigFormatEither(sourceName: String): Either<ConfigError, ConfigFormat> {
    val extension = sourceName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    return when (extension) {
        "json" -> ConfigFormat.JSON.right()
        "yaml", "yml" -> ConfigFormat.YAML.right()
        else -> ConfigError.UnsupportedConfigFormat(sourceName).left()
    }
}

internal fun validateConfigFormatEither(
    sourceName: String,
    configuredFormat: ConfigFormat,
): Either<ConfigError, ConfigFormat> =
    when (val inferredFormat = inferConfigFormatEither(sourceName)) {
        is Either.Left -> inferredFormat
        is Either.Right -> {
            if (inferredFormat.value == configuredFormat) {
                configuredFormat.right()
            } else {
                ConfigError.ConfigFormatMismatch(
                    sourceName = sourceName,
                    configuredFormat = configuredFormat,
                    actualFormat = inferredFormat.value,
                ).left()
            }
        }
    }

internal fun resolveConfigFormatEither(
    baseSourceName: String,
    customSourceName: String?,
    configuredFormat: ConfigFormat,
): Either<ConfigError, ConfigFormat> =
    if (configuredFormat == ConfigFormat.AUTO) {
        autoDetectedConfigFormatEither(baseSourceName, customSourceName)
    } else {
        explicitConfigFormatEither(baseSourceName, customSourceName, configuredFormat)
    }

private fun explicitConfigFormatEither(
    baseSourceName: String,
    customSourceName: String?,
    configuredFormat: ConfigFormat,
): Either<ConfigError, ConfigFormat> =
    when (val baseFormat = validateConfigFormatEither(baseSourceName, configuredFormat)) {
        is Either.Left -> baseFormat
        is Either.Right -> when (
            val customFormat = customSourceName?.let {
                validateConfigFormatEither(it, configuredFormat)
            }
        ) {
            null -> baseFormat
            is Either.Left -> customFormat
            is Either.Right -> configuredFormat.right()
        }
    }

private fun autoDetectedConfigFormatEither(
    baseSourceName: String,
    customSourceName: String?,
): Either<ConfigError, ConfigFormat> =
    when (val baseFormat = inferConfigFormatEither(baseSourceName)) {
        is Either.Left -> baseFormat
        is Either.Right -> {
            val customFormat = customSourceName?.let(::inferConfigFormatEither)
            when (customFormat) {
                null -> baseFormat
                is Either.Left -> customFormat
                is Either.Right -> {
                    if (customFormat.value != baseFormat.value) {
                        ConfigError.MixedConfigFormats(
                            baseSourceName = baseSourceName,
                            baseFormat = baseFormat.value,
                            customSourceName = customSourceName,
                            customFormat = customFormat.value,
                        ).left()
                    } else {
                        baseFormat.value.right()
                    }
                }
            }
        }
    }
