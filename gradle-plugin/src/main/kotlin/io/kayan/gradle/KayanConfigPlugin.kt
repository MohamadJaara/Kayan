package io.kayan.gradle

import arrow.core.getOrElse
import io.kayan.ConfigFormat
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

/** @suppress */
@OptIn(ExperimentalKayanGenerationApi::class)
public class KayanConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("kayan", KayanExtension::class.java).apply {
            baseConfigFile.convention(project.layout.projectDirectory.file("default.json"))
            configFormat.convention(ConfigFormat.JSON)
            className.convention("KayanConfig")
            jsonSchemaOutputFile.convention(
                project.layout.buildDirectory.file("generated/kayan/schema/kayan.schema.json"),
            )
            markdownSchemaOutputFile.convention(
                project.layout.buildDirectory.file("generated/kayan/schema/SCHEMA.md"),
            )
        }

        val exportSchemaTask = project.tasks.register("exportKayanSchema", ExportKayanSchemaTask::class.java) { task ->
            task.group = "documentation"
            task.description = "Exports JSON Schema and Markdown docs for the configured Kayan schema."
            task.packageName.set(extension.packageName)
            task.className.set(extension.className)
            task.schemaEntries.set(project.provider { extension.serializedSchemaEntries() })
            task.jsonSchemaOutputFile.set(extension.jsonSchemaOutputFile)
            task.markdownSchemaOutputFile.set(extension.markdownSchemaOutputFile)
        }

        val generateTask = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java) { task ->
            task.group = "code generation"
            task.description = "Generates a typed Kayan config object for the configured flavor."
            task.flavor.set(extension.flavor)
            task.kotlinPluginApplied.convention(false)
            task.declarationMode.convention(KayanDeclarationMode.OBJECT)
            task.configureCommonInputs(
                project = project,
                extension = extension,
                exportSchemaTask = exportSchemaTask,
                outputDirectory = "generated/kayan/kotlin",
            )
        }

        wireKotlinSourceSet(project, extension, KOTLIN_MULTIPLATFORM_PLUGIN_ID, "commonMain", generateTask)
        wireKotlinSourceSet(project, extension, KOTLIN_JVM_PLUGIN_ID, "main", generateTask)
        configureTargetSourceGeneration(
            project = project,
            extension = extension,
            exportSchemaTask = exportSchemaTask,
        )
        configureAndroidSourceGeneration(
            project = project,
            extension = extension,
            exportSchemaTask = exportSchemaTask,
            defaultGenerateTask = generateTask,
        )
    }

    private fun wireKotlinSourceSet(
        project: Project,
        extension: KayanExtension,
        pluginId: String,
        sourceSetName: String,
        generateTask: TaskProvider<GenerateKayanConfigTask>,
    ) {
        project.pluginManager.withPlugin(pluginId) {
            generateTask.configure { task ->
                task.kotlinPluginApplied.set(true)
                if (pluginId == KOTLIN_MULTIPLATFORM_PLUGIN_ID && sourceSetName == "commonMain") {
                    task.declarationMode.convention(
                        project.provider {
                            if (extension.targetSourceSetMappings().isEmpty()) {
                                KayanDeclarationMode.OBJECT
                            } else {
                                KayanDeclarationMode.EXPECT
                            }
                        },
                    )
                }
            }
            val kotlinExtension = project.extensions.getByType(KotlinProjectExtension::class.java)
            kotlinExtension.sourceSets.matching { sourceSet ->
                sourceSet.name == sourceSetName
            }.configureEach { sourceSet ->
                sourceSet.kotlin.srcDir(generateTask.flatMap { it.outputDir })
            }
        }
    }

    private fun configureTargetSourceGeneration(
        project: Project,
        extension: KayanExtension,
        exportSchemaTask: TaskProvider<out Task>,
    ) {
        project.pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_PLUGIN_ID) {
            project.afterEvaluate {
                configureEvaluatedTargetSourceGeneration(
                    project = project,
                    extension = extension,
                    exportSchemaTask = exportSchemaTask,
                )
            }
        }
    }

    private fun configureEvaluatedTargetSourceGeneration(
        project: Project,
        extension: KayanExtension,
        exportSchemaTask: TaskProvider<out Task>,
    ) {
        val configuredGenerations = targetSourceGenerationsEither(
            extension.targetSourceSetMappings(),
        ).getOrThrowGradle()
        if (configuredGenerations.isEmpty()) {
            return
        }

        val kotlinExtension = project.extensions.getByType(KotlinProjectExtension::class.java)
        validateConfiguredSourceSetsEither(
            availableSourceSets = kotlinExtension.sourceSets.map { sourceSet -> sourceSet.name }.toSet(),
            configuredGenerations = configuredGenerations,
        ).getOrThrowGradle()

        configuredGenerations.forEach { generation ->
            val targetTask = project.tasks.register(generation.taskName, GenerateKayanConfigTask::class.java) { task ->
                task.group = "code generation"
                task.description =
                    "Generates a typed Kayan config actual object for source set '${generation.sourceSetName}' " +
                    "and target '${generation.targetName}'."
                task.flavor.set(extension.flavor)
                task.target.set(generation.targetName)
                task.kotlinPluginApplied.set(true)
                task.declarationMode.set(KayanDeclarationMode.ACTUAL)
                task.configureCommonInputs(
                    project = project,
                    extension = extension,
                    exportSchemaTask = exportSchemaTask,
                    outputDirectory = "generated/kayan-targets/kotlin/${generation.sourceSetName}",
                )
            }

            kotlinExtension.sourceSets.matching { sourceSet ->
                sourceSet.name == generation.sourceSetName
            }.configureEach { sourceSet ->
                sourceSet.kotlin.srcDir(targetTask.flatMap { it.outputDir })
            }
        }
    }

    private fun configureAndroidSourceGeneration(
        project: Project,
        extension: KayanExtension,
        exportSchemaTask: TaskProvider<out Task>,
        defaultGenerateTask: TaskProvider<GenerateKayanConfigTask>,
    ) {
        var androidPluginApplied = false

        listOf(ANDROID_APPLICATION_PLUGIN_ID, ANDROID_LIBRARY_PLUGIN_ID).forEach { pluginId ->
            project.pluginManager.withPlugin(pluginId) {
                if (androidPluginApplied) {
                    return@withPlugin
                }
                androidPluginApplied = true

                defaultGenerateTask.configure { task ->
                    task.kotlinPluginApplied.set(true)
                }

                val androidFlavorGenerationResolver = AndroidFlavorSourceGenerationResolver(
                    project = project,
                    extension = extension,
                    exportSchemaTask = exportSchemaTask,
                    defaultGenerateTask = defaultGenerateTask,
                )

                registerAndroidGeneratedSourcesEither(
                    androidComponentsExtension = project.extensions.findByName(ANDROID_COMPONENTS_EXTENSION_NAME),
                    generationResolver = androidFlavorGenerationResolver,
                ).getOrThrowGradle()

                project.afterEvaluate {
                    configureEvaluatedAndroidSourceGeneration(androidFlavorGenerationResolver)
                }
            }
        }
    }

    private fun configureEvaluatedAndroidSourceGeneration(
        androidFlavorGenerationResolver: AndroidFlavorSourceGenerationResolver,
    ) {
        androidFlavorGenerationResolver.finalizeConfigurationEither().getOrThrowGradle()
    }

    internal companion object {
        internal const val ANDROID_EXTENSION_NAME: String = "android"
        internal const val ANDROID_COMPONENTS_EXTENSION_NAME: String = "androidComponents"
        internal const val ANDROID_APPLICATION_PLUGIN_ID: String = "com.android.application"
        internal const val ANDROID_LIBRARY_PLUGIN_ID: String = "com.android.library"
        internal const val KOTLIN_MULTIPLATFORM_PLUGIN_ID: String = "org.jetbrains.kotlin.multiplatform"
        internal const val KOTLIN_JVM_PLUGIN_ID: String = "org.jetbrains.kotlin.jvm"
    }
}

private fun <T> arrow.core.Either<KayanGradleError, T>.getOrThrowGradle(): T =
    getOrElse { throw it.toGradleException() }

internal fun registerAndroidFlavorGenerationTasks(
    project: Project,
    extension: KayanExtension,
    exportSchemaTask: TaskProvider<out Task>,
    configuredGenerations: List<AndroidFlavorSourceGeneration>,
): Map<String, TaskProvider<GenerateKayanConfigTask>> {
    val aggregateTask = project.tasks.register("generateKayanAndroidFlavorConfigs") { task ->
        task.group = "code generation"
        task.description = "Generates typed Kayan config objects for configured Android flavor source sets."
        task.dependsOn(exportSchemaTask)
    }
    val generationTasksByFlavor = linkedMapOf<String, TaskProvider<GenerateKayanConfigTask>>()

    configuredGenerations.forEach { generation ->
        val flavorTask = registerAndroidFlavorGenerationTask(
            project = project,
            extension = extension,
            exportSchemaTask = exportSchemaTask,
            generation = generation,
        )

        generationTasksByFlavor[generation.flavorName] = flavorTask
        aggregateTask.configure { task ->
            task.dependsOn(flavorTask)
        }
    }

    return generationTasksByFlavor
}

private fun registerAndroidFlavorGenerationTask(
    project: Project,
    extension: KayanExtension,
    exportSchemaTask: TaskProvider<out Task>,
    generation: AndroidFlavorSourceGeneration,
): TaskProvider<GenerateKayanConfigTask> =
    project.tasks.register(
        generation.taskName,
        GenerateKayanConfigTask::class.java,
    ) { task ->
        task.group = "code generation"
        task.description =
            "Generates a typed Kayan config object for Android flavor '${generation.flavorName}'."
        task.flavor.set(generation.flavorName)
        task.kotlinPluginApplied.set(true)
        task.declarationMode.set(KayanDeclarationMode.OBJECT)
        task.configureCommonInputs(
            project = project,
            extension = extension,
            exportSchemaTask = exportSchemaTask,
            outputDirectory = "generated/kayan/kotlin/android/${generation.flavorName}",
        )
    }

private fun GenerateKayanConfigTask.configureCommonInputs(
    project: Project,
    extension: KayanExtension,
    exportSchemaTask: TaskProvider<out Task>,
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
