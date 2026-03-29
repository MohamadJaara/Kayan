package io.kayan.gradle

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

internal fun interface AndroidVariantGenerationResolver {
    fun generationTaskForVariantEither(
        variant: Any,
    ): Either<PluginConfigurationError, TaskProvider<GenerateKayanConfigTask>?>
}

internal class AndroidFlavorSourceGenerationResolver(
    private val project: Project,
    private val extension: KayanExtension,
    private val defaultGenerateTask: TaskProvider<GenerateKayanConfigTask>,
    private val generationTaskRegistrar: GenerationTaskRegistrar,
) : AndroidVariantGenerationResolver {
    private var resolvedState: ResolvedAndroidFlavorSourceGeneration? = null

    fun finalizeConfigurationEither(): Either<PluginConfigurationError, Unit> =
        resolvedStateEither().map { Unit }

    override fun generationTaskForVariantEither(
        variant: Any,
    ): Either<PluginConfigurationError, TaskProvider<GenerateKayanConfigTask>?> =
        resolvedStateEither().map { state ->
            if (state.configuredGenerations.isEmpty()) {
                state.defaultGenerateTask
            } else {
                val flavorNames = variant.androidVariantFlavorNames()
                state.configuredGenerations.firstOrNull { generation ->
                    generation.flavorName in flavorNames
                }?.let { generation ->
                    state.generationTasksByFlavor[generation.flavorName]
                }
            }
        }

    private fun resolvedStateEither(): Either<PluginConfigurationError, ResolvedAndroidFlavorSourceGeneration> {
        resolvedState?.let { return it.right() }

        return configureStateEither().also { result ->
            if (result is Either.Right) {
                resolvedState = result.value
            }
        }
    }

    private fun configureStateEither(): Either<PluginConfigurationError, ResolvedAndroidFlavorSourceGeneration> =
        either {
            val configuredGenerations = androidFlavorSourceGenerationsEither(
                extension.androidFlavorSourceSetFlavors(),
            ).bind()

            if (configuredGenerations.isNotEmpty()) {
                validateAndroidConfiguredFlavorsEither(
                    androidExtension = project.extensions.findByName(KayanConfigPlugin.ANDROID_EXTENSION_NAME),
                    configuredFlavors = configuredGenerations,
                ).bind()
                validateAndroidFlavorDimensionsEither(
                    androidExtension = project.extensions.findByName(KayanConfigPlugin.ANDROID_EXTENSION_NAME),
                    configuredFlavors = configuredGenerations,
                ).bind()
            }

            val generationTasksByFlavor = if (configuredGenerations.isEmpty()) {
                emptyMap()
            } else {
                generationTaskRegistrar.registerAndroidFlavorGenerationTasks(configuredGenerations)
            }

            ResolvedAndroidFlavorSourceGeneration(
                configuredGenerations = configuredGenerations,
                defaultGenerateTask = defaultGenerateTask.takeIf { configuredGenerations.isEmpty() },
                generationTasksByFlavor = generationTasksByFlavor,
            )
        }
}

internal data class ResolvedAndroidFlavorSourceGeneration(
    val configuredGenerations: List<AndroidFlavorSourceGeneration>,
    val defaultGenerateTask: TaskProvider<GenerateKayanConfigTask>?,
    val generationTasksByFlavor: Map<String, TaskProvider<GenerateKayanConfigTask>>,
)
