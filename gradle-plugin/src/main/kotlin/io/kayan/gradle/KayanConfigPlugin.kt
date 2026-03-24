package io.kayan.gradle

import io.kayan.ConfigFormat
import org.gradle.api.Plugin
import org.gradle.api.Project
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
        wireKotlinSourceSet(project, KOTLIN_ANDROID_PLUGIN_ID, "main", generateTask)
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
            kotlinExtension.sourceSets.getByName(sourceSetName).kotlin.srcDir(
                generateTask.flatMap { it.outputDir },
            )
        }
    }

    internal companion object {
        internal const val KOTLIN_MULTIPLATFORM_PLUGIN_ID: String = "org.jetbrains.kotlin.multiplatform"
        internal const val KOTLIN_JVM_PLUGIN_ID: String = "org.jetbrains.kotlin.jvm"
        internal const val KOTLIN_ANDROID_PLUGIN_ID: String = "org.jetbrains.kotlin.android"
    }
}
