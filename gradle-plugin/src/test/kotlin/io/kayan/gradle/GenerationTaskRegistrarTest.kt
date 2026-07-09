package io.kayan.gradle

import io.kayan.ConfigFormat
import io.kayan.KayanValidationMode
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalKayanGenerationApi::class)
class GenerationTaskRegistrarTest {
    @Test
    fun defaultGenerateTaskUsesExtensionInputsAndExportSchemaDependency() {
        val fixture = createRegistrarFixture()

        val task = fixture.registrar.registerDefaultGenerateTask().get()

        assertEquals("code generation", task.group)
        assertEquals("prod", task.flavor.get())
        assertEquals("sample.config", task.packageName.get())
        assertEquals("KayanConfig", task.className.get())
        assertEquals(ConfigFormat.YAML, task.configFormat.get())
        assertEquals(KayanValidationMode.STRICT, task.validationMode.get())
        assertEquals(KayanDeclarationMode.OBJECT, task.declarationMode.get())
        assertEquals(false, task.kotlinPluginApplied.get())
        assertEquals(fixture.baseFile, task.baseConfigFile.get().asFile)
        assertEquals(fixture.customFile, task.customConfigFile.get().asFile)
        assertEquals(listOf("ios"), task.generatedTargetNames.get())
        assertTrue(task.outputDir.get().asFile.path.endsWith("generated/kayan/kotlin"))
        assertDependsOn(task, fixture.exportSchemaTask.get())
    }

    @Test
    fun targetGenerateTaskUsesActualModeAndTargetSpecificOutput() {
        val fixture = createRegistrarFixture()

        val task = fixture.registrar.registerTargetGenerateTask(
            TargetSourceGeneration(
                sourceSetName = "iosMain",
                targetName = "ios",
                taskName = "generateKayanIosMainConfig",
            ),
        ).get()

        assertEquals("ios", task.target.get())
        assertEquals(true, task.kotlinPluginApplied.get())
        assertEquals(KayanDeclarationMode.ACTUAL, task.declarationMode.get())
        assertTrue(task.outputDir.get().asFile.path.endsWith("generated/kayan-targets/kotlin/iosMain"))
        assertDependsOn(task, fixture.exportSchemaTask.get())
    }

    @Test
    fun androidFlavorGenerationRegistersAggregateAndFlavorTasks() {
        val fixture = createRegistrarFixture()

        val tasksByFlavor = fixture.registrar.registerAndroidFlavorGenerationTasks(
            listOf(
                AndroidFlavorSourceGeneration(
                    flavorName = "prod",
                    taskName = "generateKayanProdConfig",
                ),
                AndroidFlavorSourceGeneration(
                    flavorName = "dev",
                    taskName = "generateKayanDevConfig",
                ),
            ),
        )
        val aggregateTask = fixture.project.tasks.named("generateKayanAndroidFlavorConfigs").get()

        assertEquals(listOf("prod", "dev"), tasksByFlavor.keys.toList())
        assertEquals("prod", tasksByFlavor.getValue("prod").get().flavor.get())
        assertEquals("dev", tasksByFlavor.getValue("dev").get().flavor.get())
        assertTrue(
            tasksByFlavor.getValue("prod").get().outputDir.get().asFile.path
                .endsWith("generated/kayan-android/kotlin/prod"),
        )
        assertTrue(
            tasksByFlavor.getValue("dev").get().outputDir.get().asFile.path
                .endsWith("generated/kayan-android/kotlin/dev"),
        )
        assertDependsOn(aggregateTask, fixture.exportSchemaTask.get())
        assertDependsOn(aggregateTask, tasksByFlavor.getValue("prod").get())
        assertDependsOn(aggregateTask, tasksByFlavor.getValue("dev").get())
    }

    private fun createRegistrarFixture(): RegistrarFixture {
        val project = ProjectBuilder.builder().build()
        val baseFile = File(project.projectDir, "default.yml")
        val customFile = File(project.projectDir, "custom.yml")
        val extension = project.extensions.create("kayan", KayanExtension::class.java).apply {
            owningProject = project
            packageName.set("sample.config")
            flavor.set("prod")
            className.set("KayanConfig")
            baseConfigFile.set(baseFile)
            customConfigFile.set(customFile)
            configFormat.set(ConfigFormat.YAML)
            validationMode.set(KayanValidationMode.STRICT)
            schema {
                string("bundle_id", "BUNDLE_ID", required = true)
            }
            targetSourceSets {
                sourceSet("iosMain", "ios")
            }
        }
        val exportSchemaTask = project.tasks.register("exportKayanSchema")
        val registrar = GenerationTaskRegistrar(
            project = project,
            extension = extension,
            exportSchemaTask = exportSchemaTask,
        )

        return RegistrarFixture(
            project = project,
            registrar = registrar,
            exportSchemaTask = exportSchemaTask,
            baseFile = baseFile,
            customFile = customFile,
        )
    }

    private fun assertDependsOn(task: Task, expectedDependency: Task) {
        assertTrue(
            expectedDependency in task.taskDependencies.getDependencies(task),
            "Expected ${task.name} to depend on ${expectedDependency.name}.",
        )
    }

    private data class RegistrarFixture(
        val project: org.gradle.api.Project,
        val registrar: GenerationTaskRegistrar,
        val exportSchemaTask: org.gradle.api.tasks.TaskProvider<Task>,
        val baseFile: File,
        val customFile: File,
    )
}
