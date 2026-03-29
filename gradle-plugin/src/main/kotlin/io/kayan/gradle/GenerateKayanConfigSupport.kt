@file:Suppress("TooManyFunctions")

package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.squareup.kotlinpoet.TypeName
import io.kayan.ConfigDefinition
import io.kayan.ConfigFormat
import io.kayan.ConfigValue
import io.kayan.ConfigValueKind
import io.kayan.DefaultConfigResolver
import io.kayan.KayanValidationMode
import io.kayan.ResolvedConfigsByFlavor
import io.kayan.ResolvedFlavorConfig
import io.kayan.parserFor
import io.kayan.resolveConfigFormatEither
import org.gradle.api.GradleException
import java.io.File
import java.lang.reflect.Method

internal data class GenerationInputs(
    val packageName: String,
    val flavor: String,
    val targetName: String?,
    val className: String,
    val schema: io.kayan.ConfigSchema,
    val baseFile: File,
    val customFile: File?,
    val configFormat: ConfigFormat,
    val validationMode: KayanValidationMode,
    val declarationMode: KayanDeclarationMode,
)

internal data class LoadedCustomAdapter(
    val rawKind: ConfigValueKind?,
    val kotlinType: TypeName,
    val parse: (ConfigDefinition, Any) -> Either<GenerationError, Any>,
    val renderKotlin: (ConfigDefinition, Any) -> Either<GenerationError, String>,
)

internal fun loadedTypedAdapter(adapter: BuildTimeConfigAdapter<Any>): LoadedCustomAdapter =
    LoadedCustomAdapter(
        rawKind = adapter.rawKind,
        kotlinType = adapter.kotlinType,
        parse = { definition, rawValue ->
            runAdapterStepEither(definition, "parse") {
                adapter.parse(rawValue)
            }
        },
        renderKotlin = { definition, value ->
            runAdapterStepEither(definition, "render") {
                adapter.renderKotlin(value)
            }
        },
    )

internal fun loadedReflectiveAdapter(
    className: String,
    instance: Any,
    rawKind: ConfigValueKind?,
    kotlinType: TypeName,
    parseMethod: Method,
    renderMethod: Method,
): LoadedCustomAdapter =
    LoadedCustomAdapter(
        rawKind = rawKind,
        kotlinType = kotlinType,
        parse = { definition, rawValue ->
            reflectiveAdapterStepEither(
                definition = definition,
                action = "parse",
            ) {
                invokeAdapterMethodEither(parseMethod, instance, rawValue, className, "parse")
            }
        },
        renderKotlin = { definition, value ->
            when (
                val rendered = reflectiveAdapterStepEither(
                    definition = definition,
                    action = "render",
                ) {
                    invokeAdapterMethodEither(renderMethod, instance, value, className, "renderKotlin")
                }
            ) {
                is Either.Left -> rendered
                is Either.Right -> (rendered.value as? String)?.right()
                    ?: GenerationError.AdapterStepFailure(
                        definition = definition,
                        action = "render",
                        cause = GenerationError.AdapterRenderReturnWrongType(className).toGradleException(),
                    ).left()
            }
        },
    )

internal fun reflectiveAdapterStepEither(
    definition: ConfigDefinition,
    action: String,
    block: () -> Either<GenerationError, Any>,
): Either<GenerationError, Any> =
    when (val result = block()) {
        is Either.Left -> GenerationError.AdapterStepFailure(
            definition = definition,
            action = action,
            cause = result.value.toGradleException(),
        ).left()
        is Either.Right -> result.value.right()
    }

internal fun requireConfiguredEither(
    value: String?,
    propertyName: String,
): Either<PluginConfigurationError, String> =
    value?.trim().takeUnless { it.isNullOrEmpty() }?.right()
        ?: PluginConfigurationError.MissingRequiredProperty(propertyName).left()

internal fun requireConfigured(value: String?, propertyName: String): String =
    requireConfiguredEither(value, propertyName).getOrElse { throw it.toGradleException() }

internal fun requireSchemaEither(
    serializedEntries: List<String>,
): Either<KayanGradleError, io.kayan.ConfigSchema> {
    if (serializedEntries.isEmpty()) {
        return PluginConfigurationError.MissingSchemaEntries.left()
    }

    return when (val schema = KayanSchemaEntrySpec.toSchemaEither(serializedEntries)) {
        is Either.Left -> GenerationError.SchemaBuildFailure(schema.value).left()
        is Either.Right -> schema.value.right()
    }
}

internal fun requireSchema(serializedEntries: List<String>): io.kayan.ConfigSchema =
    requireSchemaEither(serializedEntries).getOrElse { throw it.toGradleException() }

internal fun requireExistingFileEither(
    file: File,
    label: String,
): Either<PluginConfigurationError, File> =
    if (file.exists()) {
        file.right()
    } else {
        PluginConfigurationError.MissingConfigFile(label, file.path).left()
    }

internal fun requireExistingFile(file: File, label: String): File =
    requireExistingFileEither(file, label).getOrElse { throw it.toGradleException() }

internal fun resolveConfigEither(
    schema: io.kayan.ConfigSchema,
    baseFile: File,
    customFile: File?,
    configFormat: ConfigFormat = ConfigFormat.AUTO,
    validationMode: KayanValidationMode = KayanValidationMode.SUBSET,
    targetName: String? = null,
): Either<GenerationError, ResolvedConfigsByFlavor> = either {
    val baseText = readFileEither(baseFile).bind()
    val customText = customFile?.let { readFileEither(it).bind() }
    val resolvedFormat = when (
        val result = resolveConfigFormatEither(
            baseSourceName = baseFile.absolutePath,
            customSourceName = customFile?.absolutePath,
            configuredFormat = configFormat,
        )
    ) {
        is Either.Left -> raise(GenerationError.ConfigResolutionFailure(result.value))
        is Either.Right -> result.value
    }
    val resolved = DefaultConfigResolver(parserFor(resolvedFormat)).resolveEither(
        defaultConfigJson = baseText,
        schema = schema,
        customConfigJson = customText,
        defaultConfigSourceName = baseFile.absolutePath,
        customConfigSourceName = customFile?.absolutePath ?: "custom config",
        targetName = targetName,
        validationMode = validationMode,
    )

    when (resolved) {
        is Either.Left -> raise(GenerationError.ConfigResolutionFailure(resolved.value))
        is Either.Right -> resolved.value
    }
}

internal fun resolveConfig(
    schema: io.kayan.ConfigSchema,
    baseFile: File,
    customFile: File?,
    configFormat: ConfigFormat = ConfigFormat.AUTO,
    validationMode: KayanValidationMode = KayanValidationMode.SUBSET,
    targetName: String? = null,
): ResolvedConfigsByFlavor =
    resolveConfigEither(
        schema = schema,
        baseFile = baseFile,
        customFile = customFile,
        configFormat = configFormat,
        validationMode = validationMode,
        targetName = targetName,
    ).getOrElse { throw it.toGradleException() }

internal fun requireResolvedFlavorEither(
    resolved: ResolvedConfigsByFlavor,
    flavorName: String,
): Either<GenerationError, ResolvedFlavorConfig> =
    resolved.flavors[flavorName]?.right() ?: GenerationError.MissingResolvedFlavor(flavorName).left()

internal fun requireResolvedFlavor(
    resolved: ResolvedConfigsByFlavor,
    flavorName: String,
): ResolvedFlavorConfig =
    requireResolvedFlavorEither(resolved, flavorName).getOrElse { throw it.toGradleException() }

internal fun <T> runAdapterStepEither(
    definition: ConfigDefinition,
    action: String,
    block: () -> T,
): Either<GenerationError, T> =
    when (val result = Either.catch(block)) {
        is Either.Left -> GenerationError.AdapterStepFailure(definition, action, result.value).left()
        is Either.Right -> result.value.right()
    }

internal fun <T> runAdapterStep(
    definition: ConfigDefinition,
    action: String,
    block: () -> T,
): T = runAdapterStepEither(definition, action, block).getOrElse { throw it.toGradleException() }

internal fun requireRenderedExpressionEither(
    definition: ConfigDefinition,
    rendered: String,
): Either<GenerationError, String> =
    if (rendered.isBlank()) {
        GenerationError.BlankRenderedExpression(definition).left()
    } else {
        rendered.right()
    }

internal fun requireRenderedExpression(
    definition: ConfigDefinition,
    rendered: String,
): String =
    requireRenderedExpressionEither(definition, rendered).getOrElse { throw it.toGradleException() }

internal fun ConfigValue.toRawValueEither(): Either<GenerationError, Any> = when (this) {
    is ConfigValue.StringValue -> value.right()
    is ConfigValue.BooleanValue -> value.right()
    is ConfigValue.IntValue -> value.right()
    is ConfigValue.LongValue -> value.right()
    is ConfigValue.DoubleValue -> value.right()
    is ConfigValue.StringMapValue -> value.right()
    is ConfigValue.StringListValue -> value.right()
    is ConfigValue.StringListMapValue -> value.right()
    is ConfigValue.EnumValue -> value.right()
    is ConfigValue.NullValue -> GenerationError.NullRawValue.left()
}

internal fun ConfigValue.toRawValue(): Any =
    toRawValueEither().getOrElse { error(it.message()) }

internal fun reflectiveSingleArgumentMethodEither(
    adapterClass: Class<*>,
    methodName: String,
    className: String,
): Either<GenerationError, Method> =
    adapterClass.methods
        .firstOrNull { method -> method.name == methodName && method.parameterCount == 1 }
        ?.right()
        ?: GenerationError.ReflectiveMethodMissing(className, methodName).left()

internal fun reflectiveSingleArgumentMethod(
    adapterClass: Class<*>,
    methodName: String,
    className: String,
): Method =
    reflectiveSingleArgumentMethodEither(adapterClass, methodName, className)
        .getOrElse { throw it.toGradleException() }

internal fun invokeAdapterMethodEither(
    method: Method,
    instance: Any,
    argument: Any,
    className: String,
    methodName: String,
): Either<GenerationError, Any> = either {
    val result = Either.catch {
        method.invoke(instance, argument)
    }
    val value = when (result) {
        is Either.Left -> raise(
            GenerationError.AdapterMethodInvocationFailure(
                className = className,
                methodName = methodName,
                cause = result.value,
            ),
        )
        is Either.Right -> result.value
    }

    value ?: raise(
        GenerationError.AdapterMethodInvocationFailure(
            className = className,
            methodName = methodName,
            cause = GradleException(GenerationError.AdapterReturnedNull(className, methodName).message()),
        ),
    )
}

internal fun invokeAdapterMethod(
    method: Method,
    instance: Any,
    argument: Any,
    className: String,
    methodName: String,
): Any =
    invokeAdapterMethodEither(method, instance, argument, className, methodName)
        .getOrElse { throw it.toGradleException() }

internal fun readFileEither(file: File): Either<GenerationError, String> =
    when (val result = Either.catch { file.readText() }) {
        is Either.Left -> GenerationError.FileReadFailure(file.path, result.value).left()
        is Either.Right -> result.value.right()
    }
