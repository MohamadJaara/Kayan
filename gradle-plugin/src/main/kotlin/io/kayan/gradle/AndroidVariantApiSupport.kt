package io.kayan.gradle

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.TaskProvider
import kotlin.Function1

private val outputDirectoryAccessor: Function1<GenerateKayanConfigTask, DirectoryProperty> =
    { task: GenerateKayanConfigTask -> task.outputDir }

@Suppress("ReturnCount")
internal fun registerAndroidGeneratedSourcesEither(
    androidComponentsExtension: Any?,
    defaultGenerateTask: TaskProvider<GenerateKayanConfigTask>?,
    configuredGenerations: List<AndroidFlavorSourceGeneration>,
    generationTasksByFlavor: Map<String, TaskProvider<GenerateKayanConfigTask>>,
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

    var registrationError: PluginConfigurationError? = null
    val variantAction = Action<Any> { variant ->
        if (registrationError != null) {
            return@Action
        }

        registrationError = when (
            val result = registerVariantGeneratedSourcesEither(
                variant = variant,
                defaultGenerateTask = defaultGenerateTask,
                configuredGenerations = configuredGenerations,
                generationTasksByFlavor = generationTasksByFlavor,
            )
        ) {
            is Either.Left -> result.value
            is Either.Right -> null
        }
    }

    return runCatching {
        onVariantsMethod.invoke(androidComponentsExtension, allSelector, variantAction)
    }.fold(
        onSuccess = {
            registrationError?.left() ?: Unit.right()
        },
        onFailure = { cause ->
            PluginConfigurationError.UnsupportedAndroidVariantApi(
                detail = "Failed to register generated Kotlin sources with onVariants(selector, action).",
                cause = cause,
            ).left()
        },
    )
}

private fun registerVariantGeneratedSourcesEither(
    variant: Any,
    defaultGenerateTask: TaskProvider<GenerateKayanConfigTask>?,
    configuredGenerations: List<AndroidFlavorSourceGeneration>,
    generationTasksByFlavor: Map<String, TaskProvider<GenerateKayanConfigTask>>,
): Either<PluginConfigurationError, Unit> {
    val taskProvider = if (configuredGenerations.isEmpty()) {
        defaultGenerateTask
    } else {
        val flavorNames = variant.androidVariantFlavorNames()
        configuredGenerations.firstOrNull { generation ->
            generation.flavorName in flavorNames
        }?.let { generation ->
            generationTasksByFlavor[generation.flavorName]
        }
    } ?: return Unit.right()

    return variant.registerGeneratedKotlinSourceDirectoryEither(taskProvider)
}

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

private fun Any.androidVariantFlavorNames(): Set<String> {
    val productFlavors = invokeNoArgOrNull("getProductFlavors") as? Iterable<*> ?: return emptySet()

    return productFlavors.mapNotNullTo(linkedSetOf()) { productFlavor ->
        productFlavor?.readStringPropertyOrNull("getSecond")
            ?: productFlavor?.readStringPropertyOrNull("getName")
            ?: productFlavor?.readStringPropertyOrNull("getFlavorName")
    }
}

private fun Any.variantName(): String =
    readStringPropertyOrNull("getName").orEmpty().ifBlank { "<unknown>" }
