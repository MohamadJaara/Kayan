package io.kayan.gradle

import arrow.core.getOrElse
import io.kayan.ConfigFormat
import io.kayan.KayanValidationMode
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

/** @suppress */
@OptIn(ExperimentalKayanGenerationApi::class)
public class KayanConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("kayan", KayanExtension::class.java).apply {
            baseConfigFile.convention(project.layout.projectDirectory.file("default.json"))
            configFormat.convention(ConfigFormat.JSON)
            validationMode.convention(KayanValidationMode.SUBSET)
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
        val generationTaskRegistrar = GenerationTaskRegistrar(
            project = project,
            extension = extension,
            exportSchemaTask = exportSchemaTask,
        )

        val generateTask = generationTaskRegistrar.registerDefaultGenerateTask()

        wireKotlinSourceSet(project, extension, KOTLIN_MULTIPLATFORM_PLUGIN_ID, "commonMain", generateTask)
        wireKotlinSourceSet(project, extension, KOTLIN_JVM_PLUGIN_ID, "main", generateTask)
        configureTargetSourceGeneration(
            project = project,
            extension = extension,
            generationTaskRegistrar = generationTaskRegistrar,
        )
        configureAndroidSourceGeneration(
            project = project,
            extension = extension,
            defaultGenerateTask = generateTask,
            generationTaskRegistrar = generationTaskRegistrar,
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
        generationTaskRegistrar: GenerationTaskRegistrar,
    ) {
        project.pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_PLUGIN_ID) {
            project.afterEvaluate {
                configureEvaluatedTargetSourceGeneration(
                    project = project,
                    extension = extension,
                    generationTaskRegistrar = generationTaskRegistrar,
                )
            }
        }
    }

    private fun configureEvaluatedTargetSourceGeneration(
        project: Project,
        extension: KayanExtension,
        generationTaskRegistrar: GenerationTaskRegistrar,
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
            val targetTask = generationTaskRegistrar.registerTargetGenerateTask(generation)

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
        defaultGenerateTask: TaskProvider<GenerateKayanConfigTask>,
        generationTaskRegistrar: GenerationTaskRegistrar,
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
                    defaultGenerateTask = defaultGenerateTask,
                    generationTaskRegistrar = generationTaskRegistrar,
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
