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
        project.pluginManager.withPlugin(KOTLIN_ANDROID_PLUGIN_ID) {
            defaultGenerateTask.configure { task ->
                task.kotlinPluginApplied.set(true)
            }

            project.afterEvaluate {
                val configuredGenerations = androidFlavorSourceGenerationsEither(
                    extension.androidFlavorSourceSetFlavors(),
                ).getOrElse { throw it.toGradleException() }

                if (configuredGenerations.isEmpty()) {
                    wireAndroidSourceSet(project, "main", defaultGenerateTask)
                    return@afterEvaluate
                }

                val kotlinExtension = project.extensions.getByType(KotlinProjectExtension::class.java)
                validateAndroidFlavorSourceSetsEither(
                    configuredFlavors = configuredGenerations,
                    availableSourceSetNames = kotlinExtension.sourceSets.names,
                ).getOrElse { throw it.toGradleException() }

                val aggregateTask = project.tasks.register("generateKayanAndroidFlavorConfigs") { task ->
                    task.group = "code generation"
                    task.description = "Generates typed Kayan config objects for configured Android flavor source sets."
                    task.dependsOn(exportSchemaTask)
                }

                configuredGenerations.forEach { generation ->
                    val flavorTask = project.tasks.register(
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

                    aggregateTask.configure { task ->
                        task.dependsOn(flavorTask)
                    }
                    wireAndroidSourceSet(project, generation.flavorName, flavorTask)
                }
            }
        }
    }

    private fun wireAndroidSourceSet(
        project: Project,
        sourceSetName: String,
        generateTask: TaskProvider<GenerateKayanConfigTask>,
    ) {
        val kotlinExtension = project.extensions.getByType(KotlinProjectExtension::class.java)
        kotlinExtension.sourceSets.matching { sourceSet ->
            sourceSet.name == sourceSetName
        }.configureEach { sourceSet ->
            sourceSet.kotlin.srcDir(generateTask.flatMap { it.outputDir })
        }
    }

    internal companion object {
        internal const val KOTLIN_MULTIPLATFORM_PLUGIN_ID: String = "org.jetbrains.kotlin.multiplatform"
        internal const val KOTLIN_JVM_PLUGIN_ID: String = "org.jetbrains.kotlin.jvm"
        internal const val KOTLIN_ANDROID_PLUGIN_ID: String = "org.jetbrains.kotlin.android"
    }
}
