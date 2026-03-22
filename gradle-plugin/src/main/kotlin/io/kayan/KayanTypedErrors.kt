package io.kayan

import kotlinx.serialization.SerializationException

internal interface KayanError

internal sealed interface SchemaError : KayanError {
    val cause: Throwable?

    fun message(): String

    data class InvalidSerializedJson(
        val entryIndex: Int,
        val detail: String?,
        override val cause: Throwable,
    ) : SchemaError {
        override fun message(): String = detail.orEmpty()
    }

    data class InvalidSerializedRoot(
        val entryIndex: Int,
    ) : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String = "Invalid serialized Kayan schema entry."
    }

    data class MissingRequiredField(
        val entryIndex: Int,
        val fieldName: String,
    ) : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String = "Missing required Kayan schema entry field '$fieldName'."
    }

    data class InvalidEnumKind(
        val entryIndex: Int,
        val rawValue: String,
        override val cause: Throwable,
    ) : SchemaError {
        override fun message(): String = "Invalid value '$rawValue' for Kayan schema entry field 'kind'."
    }

    data class BlankJsonKey(
        val entryIndex: Int,
    ) : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String = "Config definition jsonKey must not be blank."
    }

    data class BlankPropertyName(
        val entryIndex: Int,
    ) : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String = "Config definition propertyName must not be blank."
    }

    data class RequiredAndNullable(
        val entryIndex: Int,
        val jsonKey: String,
    ) : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String = "Config definition '$jsonKey' cannot be both required and nullable."
    }

    data class EnumTypeOnNonEnum(
        val entryIndex: Int,
        val jsonKey: String,
    ) : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String = "Config definition '$jsonKey' can only set enumTypeName for enum values."
    }

    data class MissingEnumType(
        val entryIndex: Int,
        val jsonKey: String,
    ) : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String = "Config definition '$jsonKey' must declare enumTypeName for enum values."
    }

    data class BlankAdapterClassName(
        val entryIndex: Int,
    ) : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String = "Config definition adapterClassName must not be blank."
    }

    data object EmptySchema : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String = "Config schema must contain at least one definition."
    }

    data class DuplicateJsonKeys(
        val jsonKeys: List<String>,
    ) : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Config schema contains duplicate jsonKey values: ${jsonKeys.joinToString { "'$it'" }}."
    }

    data class DuplicatePropertyNames(
        val propertyNames: List<String>,
    ) : SchemaError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Config schema contains duplicate propertyName values: ${propertyNames.joinToString { "'$it'" }}."
    }

    data class UnexpectedDefinitionValidation(
        override val cause: Throwable,
    ) : SchemaError {
        override fun message(): String = cause.message.orEmpty()
    }

    data class UnexpectedSchemaValidation(
        override val cause: Throwable,
    ) : SchemaError {
        override fun message(): String = cause.message.orEmpty()
    }
}

internal sealed interface ConfigError : KayanError {
    fun toConfigValidationException(): ConfigValidationException

    data class InvalidJson(
        val sourceName: String,
        val detail: String?,
        val cause: SerializationException,
    ) : ConfigError {
        override fun toConfigValidationException(): ConfigValidationException = ConfigValidationException(
            "Invalid JSON in source '$sourceName': $detail",
            cause,
        )
    }

    data class MissingRequiredFlavorsObject(
        val context: DiagnosticContext,
    ) : ConfigError {
        override fun toConfigValidationException(): ConfigValidationException = ConfigValidationException(
            "Missing required '${DefaultConfigResolver.FLAVORS_KEY}' object in ${context.describe()}."
        )
    }

    data class InvalidType(
        val subject: String,
        val expectedType: String,
        val actualType: String,
        val context: DiagnosticContext,
    ) : ConfigError {
        override fun toConfigValidationException(): ConfigValidationException = ConfigValidationException(
            "Invalid $subject in ${context.describe()}: expected $expectedType but found $actualType."
        )
    }

    data class UnknownKey(
        val jsonKey: String,
        val context: DiagnosticContext,
        val suggestions: List<String>,
    ) : ConfigError {
        override fun toConfigValidationException(): ConfigValidationException {
            val suggestionMessage = when (suggestions.size) {
                0 -> ""
                1 -> " Did you mean '${suggestions.single()}'?"
                else -> " Close matches: ${suggestions.joinToString { "'$it'" }}."
            }

            return ConfigValidationException(
                "Unknown key '$jsonKey' in ${context.describe()}.$suggestionMessage"
            )
        }
    }

    data class UnknownFlavorInCustomConfig(
        val customFlavor: String,
        val customContext: DiagnosticContext,
        val defaultConfigSourceName: String,
    ) : ConfigError {
        override fun toConfigValidationException(): ConfigValidationException = ConfigValidationException(
            "Flavor '$customFlavor' in ${customContext.describe()} does not exist in source " +
                "'$defaultConfigSourceName'."
        )
    }

    data class MissingRequiredResolvedKey(
        val flavorName: String,
        val definition: ConfigDefinition,
    ) : ConfigError {
        override fun toConfigValidationException(): ConfigValidationException = ConfigValidationException(
            "Resolved flavor '$flavorName' is missing required key '${definition.jsonKey}' at path '${
                DiagnosticContext(DefaultConfigResolver.RESOLVED_CONFIG_SOURCE_NAME)
                    .atFlavor(flavorName)
                    .atKey(definition.jsonKey)
                    .path
            }'."
        )
    }

    data class InvalidEnumValue(
        val jsonKey: String,
        val context: DiagnosticContext,
        val detail: String,
        val cause: Throwable? = null,
    ) : ConfigError {
        override fun toConfigValidationException(): ConfigValidationException = ConfigValidationException(
            "Invalid value for key '$jsonKey' in ${context.describe()}: $detail",
            cause,
        )
    }
}

internal data class DiagnosticContext(
    val sourceName: String,
    val flavorName: String? = null,
    private val segments: List<PathSegment> = emptyList(),
) {
    val path: String
        get() = buildString {
            append('$')
            segments.forEach { segment ->
                when (segment) {
                    is PathSegment.Index -> append("[${segment.value}]")
                    is PathSegment.Key -> {
                        if (DefaultConfigResolver.IDENTIFIER_SEGMENT.matches(segment.value)) {
                            append('.')
                            append(segment.value)
                        } else {
                            append("[\"")
                            append(escapeJsonPathKey(segment.value))
                            append("\"]")
                        }
                    }
                }
            }
        }

    fun atKey(key: String): DiagnosticContext = copy(segments = segments + PathSegment.Key(key))

    fun atIndex(index: Int): DiagnosticContext = copy(segments = segments + PathSegment.Index(index))

    fun atFlavor(flavorName: String): DiagnosticContext = copy(
        flavorName = flavorName,
        segments = segments + PathSegment.Key(DefaultConfigResolver.FLAVORS_KEY) + PathSegment.Key(flavorName),
    )

    fun describe(): String = buildString {
        append("source '")
        append(sourceName)
        append('\'')
        flavorName?.let {
            append(", flavor '")
            append(it)
            append('\'')
        }
        append(", path '")
        append(path)
        append('\'')
    }

    private fun escapeJsonPathKey(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}

internal sealed interface PathSegment {
    data class Key(val value: String) : PathSegment
    data class Index(val value: Int) : PathSegment
}
