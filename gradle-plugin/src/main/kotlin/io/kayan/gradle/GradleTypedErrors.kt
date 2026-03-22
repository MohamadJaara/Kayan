package io.kayan.gradle

import arrow.core.NonEmptyList
import io.kayan.ConfigDefinition
import io.kayan.ConfigError
import io.kayan.ConfigValueKind
import io.kayan.KayanError
import io.kayan.SchemaError
import org.gradle.api.GradleException

internal sealed interface KayanGradleError : KayanError {
    val cause: Throwable?

    fun message(): String

    fun toGradleException(): GradleException = GradleException(message(), cause)
}

internal sealed interface PluginConfigurationError : KayanGradleError {
    data class MissingRequiredProperty(
        val propertyName: String,
    ) : PluginConfigurationError {
        override val cause: Throwable? = null

        override fun message(): String = "Kayan requires `$propertyName` to be configured."
    }

    data object MissingSchemaEntries : PluginConfigurationError {
        override val cause: Throwable? = null

        override fun message(): String = "Kayan requires at least one consumer-defined schema entry."
    }

    data class MissingConfigFile(
        val fileLabel: String,
        val path: String,
    ) : PluginConfigurationError {
        override val cause: Throwable? = null

        override fun message(): String = "Kayan $fileLabel config file does not exist: $path"
    }

    data object MissingKotlinMultiplatformPlugin : PluginConfigurationError {
        override val cause: Throwable? = null

        override fun message(): String =
            "The `io.kayan.config` plugin requires `org.jetbrains.kotlin.multiplatform`."
    }
}

internal sealed interface GenerationError : KayanGradleError {
    data class SchemaBuildFailure(
        val errors: NonEmptyList<SchemaError>,
    ) : GenerationError {
        override val cause: Throwable? = errors.first().cause

        override fun message(): String = errors.joinToString(
            separator = "\n",
            prefix = "Failed to build Kayan schema:\n",
        ) { "- ${it.message()}" }
    }

    data class ConfigResolutionFailure(
        val error: ConfigError,
    ) : GenerationError {
        private val configValidationException = error.toConfigValidationException()

        override val cause: Throwable? = configValidationException

        override fun message(): String =
            "Failed to resolve Kayan config: ${configValidationException.message}"
    }

    data class MissingResolvedFlavor(
        val flavorName: String,
    ) : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Configured Kayan flavor '$flavorName' was not found in the resolved config."
    }

    data class AdapterStepFailure(
        val definition: ConfigDefinition,
        val action: String,
        override val cause: Throwable,
    ) : GenerationError {
        override fun message(): String =
            "Failed to $action key '${definition.jsonKey}' with custom adapter '${definition.adapterClassName}': " +
                "${cause.message}"
    }

    data class BlankRenderedExpression(
        val definition: ConfigDefinition,
    ) : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Custom adapter '${definition.adapterClassName}' for key '${definition.jsonKey}' " +
                "returned a blank Kotlin expression."
    }

    data object NullRawValue : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String = "Null config values do not have a raw adapter value."
    }

    data class ReflectiveMethodMissing(
        val className: String,
        val methodName: String,
    ) : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Custom adapter '$className' must define a '$methodName' method that accepts exactly one argument."
    }

    data class AdapterMethodInvocationFailure(
        val className: String,
        val methodName: String,
        override val cause: Throwable,
    ) : GenerationError {
        override fun message(): String =
            "Failed to invoke '$methodName' on custom adapter '$className': ${cause.message}"
    }

    data class AdapterReturnedNull(
        val className: String,
        val methodName: String,
    ) : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Custom adapter '$className' returned null from '$methodName'."
    }

    data class AdapterClassNotFound(
        val className: String,
    ) : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Failed to load custom adapter class '$className'. Ensure it is on the Gradle build classpath."
    }

    data class AdapterInstantiationFailure(
        val className: String,
        override val cause: Throwable,
    ) : GenerationError {
        override fun message(): String =
            "Custom adapter class '$className' must be a Kotlin object or expose a public zero-argument constructor."
    }

    data class MissingAdapterProperty(
        val className: String,
        val propertyName: String,
        override val cause: Throwable,
    ) : GenerationError {
        override fun message(): String =
            "Custom adapter '$className' must expose a '$propertyName' property or getter."
    }

    data class AdapterPropertyWrongType(
        val className: String,
        val propertyName: String,
        val expectedType: String,
    ) : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Custom adapter '$className' must expose '$propertyName' as a $expectedType."
    }

    data class AdapterRawKindMismatch(
        val definition: ConfigDefinition,
        val actualKind: ConfigValueKind,
    ) : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Custom adapter '${definition.adapterClassName}' for key '${definition.jsonKey}' declares raw kind " +
                "'$actualKind', but the schema expects '${definition.kind}'."
    }

    data class BlankAdapterKotlinType(
        val definition: ConfigDefinition,
    ) : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Custom adapter '${definition.adapterClassName}' for key '${definition.jsonKey}' " +
                "returned a blank kotlinType."
    }

    data class AdapterRenderReturnWrongType(
        val className: String,
    ) : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String =
            "Custom adapter '$className' must return a String from renderKotlin."
    }

    data class DirectoryCreationFailure(
        val path: String,
    ) : GenerationError {
        override val cause: Throwable? = null

        override fun message(): String = "Failed to create directory: $path"
    }

    data class FileWriteFailure(
        val path: String,
        override val cause: Throwable,
    ) : GenerationError {
        override fun message(): String = "Failed to write file '$path': ${cause.message}"
    }

    data class FileReadFailure(
        val path: String,
        override val cause: Throwable,
    ) : GenerationError {
        override fun message(): String = "Failed to read file '$path': ${cause.message}"
    }

    data class SourceGenerationFailure(
        val targetName: String,
        override val cause: Throwable,
    ) : GenerationError {
        override fun message(): String = "Failed to generate $targetName: ${cause.message}"
    }
}
