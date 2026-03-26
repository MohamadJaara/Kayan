package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.TaskProvider
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException
import kotlin.Function1

private val outputDirectoryAccessor: Function1<GenerateKayanConfigTask, DirectoryProperty> =
    { task: GenerateKayanConfigTask -> task.outputDir }

internal fun registerAndroidGeneratedSourcesEither(
    androidComponentsExtension: Any?,
    defaultGenerateTask: TaskProvider<GenerateKayanConfigTask>?,
    configuredGenerations: List<AndroidFlavorSourceGeneration>,
    generationTasksByFlavor: Map<String, TaskProvider<GenerateKayanConfigTask>>,
): Either<PluginConfigurationError, Unit> =
    registerAndroidGeneratedSourcesEither(
        androidComponentsExtension = androidComponentsExtension,
        generationResolver = FixedAndroidVariantGenerationResolver(
            defaultGenerateTask = defaultGenerateTask,
            configuredGenerations = configuredGenerations,
            generationTasksByFlavor = generationTasksByFlavor,
        ),
    )

@Suppress("ReturnCount")
internal fun registerAndroidGeneratedSourcesEither(
    androidComponentsExtension: Any?,
    generationResolver: AndroidVariantGenerationResolver,
): Either<PluginConfigurationError, Unit> {
    if (androidComponentsExtension == null) {
        return PluginConfigurationError.UnsupportedAndroidVariantApi(
            detail = "The Android `androidComponents` extension was not found.",
        ).left()
    }

    val selector = androidComponentsExtension.invokeNoArgOrNull("selector")
        ?: return PluginConfigurationError.UnsupportedAndroidVariantApi(
            detail = "The Android `androidComponents` extension does not expose selector().",
        ).left()
    val allSelector = selector.invokeNoArgOrNull("all")
        ?: return PluginConfigurationError.UnsupportedAndroidVariantApi(
            detail = "The Android Variant API selector does not expose all().",
        ).left()
    val onVariantsMethod = androidComponentsExtension.javaClass.methods.firstOrNull { method ->
        method.name == "onVariants" &&
            method.parameterCount == 2 &&
            Action::class.java.isAssignableFrom(method.parameterTypes[1])
    } ?: return PluginConfigurationError.UnsupportedAndroidVariantApi(
        detail = "The Android `androidComponents` extension does not expose onVariants(selector, action).",
    ).left()

    val variantAction = Action<Any> { variant ->
        val generateTask = generationResolver.generationTaskForVariantEither(variant).getOrElse { error ->
            throw AndroidGeneratedSourceRegistrationException(error)
        }

        generateTask?.let { taskProvider ->
            registerVariantGeneratedSourcesEither(
                variant = variant,
                generateTask = taskProvider,
            ).getOrElse { error ->
                throw AndroidGeneratedSourceRegistrationException(error)
            }
        }
    }

    return runCatching {
        onVariantsMethod.invoke(androidComponentsExtension, allSelector, variantAction)
    }.fold(
        onSuccess = { Unit.right() },
        onFailure = { cause ->
            val rootCause = cause.unwrapReflectionFailure()
            (rootCause as? AndroidGeneratedSourceRegistrationException)?.error?.left()
                ?: PluginConfigurationError.UnsupportedAndroidVariantApi(
                    detail = "Failed to register generated Kotlin sources with onVariants(selector, action).",
                    cause = rootCause,
                ).left()
        },
    )
}

private fun registerVariantGeneratedSourcesEither(
    variant: Any,
    generateTask: TaskProvider<GenerateKayanConfigTask>,
): Either<PluginConfigurationError, Unit> =
    variant.registerGeneratedKotlinSourceDirectoryEither(generateTask)

@Suppress("ReturnCount")
private fun Any.registerGeneratedKotlinSourceDirectoryEither(
    generateTask: TaskProvider<GenerateKayanConfigTask>,
): Either<PluginConfigurationError, Unit> {
    val variantName = variantName()
    val sources = invokeNoArgOrNull("getSources")
        ?: return PluginConfigurationError.UnsupportedAndroidVariantApi(
            detail = "Android variant '$variantName' does not expose getSources().",
        ).left()
    val kotlinSources = sources.invokeNoArgOrNull("getKotlin")
        ?: return PluginConfigurationError.UnsupportedAndroidVariantApi(
            detail = "Android variant '$variantName' does not expose sources.kotlin.",
        ).left()
    val addGeneratedSourceDirectoryMethod = kotlinSources.javaClass.methods.firstOrNull { method ->
        method.name == "addGeneratedSourceDirectory" &&
            method.parameterCount == 2 &&
            method.parameterTypes[1].isAssignableFrom(Function1::class.java)
    } ?: return PluginConfigurationError.UnsupportedAndroidVariantApi(
        detail = "Android variant '$variantName' does not expose kotlin.addGeneratedSourceDirectory(task, accessor).",
    ).left()

    return runCatching {
        addGeneratedSourceDirectoryMethod.invoke(kotlinSources, generateTask, outputDirectoryAccessor)
    }.fold(
        onSuccess = { Unit.right() },
        onFailure = { cause ->
            PluginConfigurationError.UnsupportedAndroidVariantApi(
                detail = "Failed to register generated Kotlin sources for Android variant '$variantName'.",
                cause = cause,
            ).left()
        },
    )
}

internal fun Any.androidVariantFlavorNames(): Set<String> {
    val productFlavors = invokeNoArgOrNull("getProductFlavors") as? Iterable<*> ?: return emptySet()

    return productFlavors.mapNotNullTo(linkedSetOf()) { productFlavor ->
        productFlavor?.readStringPropertyOrNull("getSecond")
            ?: productFlavor?.readStringPropertyOrNull("getName")
            ?: productFlavor?.readStringPropertyOrNull("getFlavorName")
    }
}

private fun Any.variantName(): String =
    readStringPropertyOrNull("getName").orEmpty().ifBlank { "<unknown>" }

private class FixedAndroidVariantGenerationResolver(
    private val defaultGenerateTask: TaskProvider<GenerateKayanConfigTask>?,
    private val configuredGenerations: List<AndroidFlavorSourceGeneration>,
    private val generationTasksByFlavor: Map<String, TaskProvider<GenerateKayanConfigTask>>,
) : AndroidVariantGenerationResolver {
    override fun generationTaskForVariantEither(
        variant: Any,
    ): Either<PluginConfigurationError, TaskProvider<GenerateKayanConfigTask>?> =
        if (configuredGenerations.isEmpty()) {
            defaultGenerateTask.right()
        } else {
            val flavorNames = variant.androidVariantFlavorNames()
            configuredGenerations.firstOrNull { generation ->
                generation.flavorName in flavorNames
            }?.let { generation ->
                generationTasksByFlavor[generation.flavorName]
            }.right()
        }
}

private class AndroidGeneratedSourceRegistrationException(
    val error: PluginConfigurationError,
) : GradleException(error.message(), error.cause)

private fun Throwable.unwrapReflectionFailure(): Throwable = when (this) {
    is InvocationTargetException -> targetException ?: this
    is UndeclaredThrowableException -> undeclaredThrowable ?: this
    else -> this
}
