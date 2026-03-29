package io.kayan.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

internal class GenerationTaskRegistrar(
    private val project: Project,
    private val extension: KayanExtension,
    private val exportSchemaTask: TaskProvider<out Task>,
) {
    fun registerDefaultGenerateTask(): TaskProvider<GenerateKayanConfigTask> =
        registerGenerateTask(
            spec = GenerationSpec(
                taskName = "generateKayanConfig",
                description = "Generates a typed Kayan config object for the configured flavor.",
                outputDirectory = "generated/kayan/kotlin",
                kotlinPluginApplied = false,
                declarationMode = KayanDeclarationMode.OBJECT,
                useConventions = true,
            ),
        )

    fun registerTargetGenerateTask(
        generation: TargetSourceGeneration,
    ): TaskProvider<GenerateKayanConfigTask> =
        registerGenerateTask(
            spec = GenerationSpec(
                taskName = generation.taskName,
                description =
                    "Generates a typed Kayan config actual object for source set '${generation.sourceSetName}' " +
                        "and target '${generation.targetName}'.",
                outputDirectory = "generated/kayan-targets/kotlin/${generation.sourceSetName}",
                targetName = generation.targetName,
                kotlinPluginApplied = true,
                declarationMode = KayanDeclarationMode.ACTUAL,
            ),
        )

    fun registerAndroidFlavorGenerationTasks(
        configuredGenerations: List<AndroidFlavorSourceGeneration>,
    ): Map<String, TaskProvider<GenerateKayanConfigTask>> {
        val aggregateTask = project.tasks.register("generateKayanAndroidFlavorConfigs") { task ->
            task.group = "code generation"
            task.description = "Generates typed Kayan config objects for configured Android flavor source sets."
            task.dependsOn(exportSchemaTask)
        }
        val generationTasksByFlavor = linkedMapOf<String, TaskProvider<GenerateKayanConfigTask>>()

        configuredGenerations.forEach { generation ->
            val flavorTask = registerAndroidFlavorGenerationTask(generation)
            generationTasksByFlavor[generation.flavorName] = flavorTask
            aggregateTask.configure { task ->
                task.dependsOn(flavorTask)
            }
        }

        return generationTasksByFlavor
    }

    private fun registerAndroidFlavorGenerationTask(
        generation: AndroidFlavorSourceGeneration,
    ): TaskProvider<GenerateKayanConfigTask> =
        registerGenerateTask(
            spec = GenerationSpec(
                taskName = generation.taskName,
                description =
                    "Generates a typed Kayan config object for Android flavor '${generation.flavorName}'.",
                outputDirectory = "generated/kayan/kotlin/android/${generation.flavorName}",
                flavorName = generation.flavorName,
                kotlinPluginApplied = true,
                declarationMode = KayanDeclarationMode.OBJECT,
            ),
        )

    private fun registerGenerateTask(
        spec: GenerationSpec,
    ): TaskProvider<GenerateKayanConfigTask> =
        project.tasks.register(spec.taskName, GenerateKayanConfigTask::class.java) { task ->
            task.group = "code generation"
            task.description = spec.description
            if (spec.flavorName == null) {
                task.flavor.set(extension.flavor)
            } else {
                task.flavor.set(spec.flavorName)
            }
            spec.targetName?.let(task.target::set)
            if (spec.useConventions) {
                task.kotlinPluginApplied.convention(spec.kotlinPluginApplied)
                task.declarationMode.convention(spec.declarationMode)
            } else {
                task.kotlinPluginApplied.set(spec.kotlinPluginApplied)
                task.declarationMode.set(spec.declarationMode)
            }
            task.configureCommonInputs(spec.outputDirectory)
        }

    private fun GenerateKayanConfigTask.configureCommonInputs(
        outputDirectory: String,
    ) {
        packageName.set(extension.packageName)
        className.set(extension.className)
        baseConfigFile.set(extension.baseConfigFile)
        customConfigFile.set(extension.customConfigFile)
        configFormat.set(extension.configFormat)
        schemaEntries.set(project.provider { extension.serializedSchemaEntries() })
        outputDir.set(project.layout.buildDirectory.dir(outputDirectory))
        project.buildscript.configurations.findByName("classpath")?.let { classpath ->
            buildscriptClasspath.from(classpath)
        }
        dependsOn(exportSchemaTask)
    }
}

private data class GenerationSpec(
    val taskName: String,
    val description: String,
    val outputDirectory: String,
    val flavorName: String? = null,
    val targetName: String? = null,
    val kotlinPluginApplied: Boolean,
    val declarationMode: KayanDeclarationMode,
    val useConventions: Boolean = false,
)
