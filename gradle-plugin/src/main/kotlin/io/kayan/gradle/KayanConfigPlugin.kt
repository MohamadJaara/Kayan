package io.kayan.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

public class KayanConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("kayan", KayanExtension::class.java).apply {
            baseConfigFile.convention(project.layout.projectDirectory.file("default.json"))
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
            task.kotlinMultiplatformPluginApplied.convention(false)
            task.baseConfigFile.set(extension.baseConfigFile)
            task.customConfigFile.set(extension.customConfigFile)
            task.schemaEntries.set(project.provider { extension.serializedSchemaEntries() })
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/commonMain/kotlin"))
            project.buildscript.configurations.findByName("classpath")?.let { classpath ->
                task.buildscriptClasspath.from(classpath)
            }
            task.dependsOn(exportSchemaTask)
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            generateTask.configure { task ->
                task.kotlinMultiplatformPluginApplied.set(true)
            }
            val kotlinExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlinExtension.sourceSets.getByName("commonMain").kotlin.srcDir(generateTask.flatMap { it.outputDir })
        }
    }
}
