package io.kayan.gradle

import arrow.core.getOrElse
import io.kayan.ConfigFormat
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

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
            task.packageName.set(extension.packageName)
            task.flavor.set(extension.flavor)
            task.className.set(extension.className)
            task.kotlinPluginApplied.convention(false)
            task.baseConfigFile.set(extension.baseConfigFile)
            task.customConfigFile.set(extension.customConfigFile)
            task.configFormat.set(extension.configFormat)
            task.schemaEntries.set(project.provider { extension.serializedSchemaEntries() })
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/kotlin"))
            project.buildscript.configurations.findByName("classpath")?.let { classpath ->
                task.buildscriptClasspath.from(classpath)
            }
            task.dependsOn(exportSchemaTask)
        }

        wireKotlinSourceSet(project, KOTLIN_MULTIPLATFORM_PLUGIN_ID, "commonMain", generateTask)
        wireKotlinSourceSet(project, KOTLIN_JVM_PLUGIN_ID, "main", generateTask)
        configureAndroidSourceGeneration(
            project = project,
            extension = extension,
            exportSchemaTask = exportSchemaTask,
            defaultGenerateTask = generateTask,
        )
    }

    private fun wireKotlinSourceSet(
        project: Project,
        pluginId: String,
        sourceSetName: String,
        generateTask: TaskProvider<GenerateKayanConfigTask>,
    ) {
        project.pluginManager.withPlugin(pluginId) {
            generateTask.configure { task ->
                task.kotlinPluginApplied.set(true)
            }
            val kotlinExtension = project.extensions.getByType(KotlinProjectExtension::class.java)
            kotlinExtension.sourceSets.matching { sourceSet ->
                sourceSet.name == sourceSetName
            }.configureEach { sourceSet ->
                sourceSet.kotlin.srcDir(generateTask.flatMap { it.outputDir })
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
        task.packageName.set(extension.packageName)
        task.flavor.set(generation.flavorName)
        task.className.set(extension.className)
        task.kotlinPluginApplied.set(true)
        task.baseConfigFile.set(extension.baseConfigFile)
        task.customConfigFile.set(extension.customConfigFile)
        task.configFormat.set(extension.configFormat)
        task.schemaEntries.set(project.provider { extension.serializedSchemaEntries() })
        task.outputDir.set(
            project.layout.buildDirectory.dir(
                "generated/kayan/kotlin/android/${generation.outputDirectorySegment}",
            ),
        )
        project.buildscript.configurations.findByName("classpath")?.let { classpath ->
            task.buildscriptClasspath.from(classpath)
        }
        task.dependsOn(exportSchemaTask)
    }
