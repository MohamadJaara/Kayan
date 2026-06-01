package io.kayan.gradle

import io.kayan.ConfigFormat
import io.kayan.KayanValidationMode
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KayanConfigPluginTest {
    @Test
    fun applyCreatesRootAndProjectExtensionsWithConventions() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply(KayanConfigPlugin::class.java)

        val rootExtension = project.extensions.getByType(KayanRootExtension::class.java)
        val extension = project.extensions.getByType(KayanExtension::class.java)
        assertEquals("default.json", rootExtension.baseConfigFile.get().asFile.name)
        assertEquals(ConfigFormat.JSON, rootExtension.configFormat.get())
        assertEquals(KayanValidationMode.SUBSET, rootExtension.validationMode.get())
        assertEquals("default.json", extension.baseConfigFile.get().asFile.name)
        assertEquals("KayanConfig", extension.className.get())
        assertTrue(
            extension.jsonSchemaOutputFile.get().asFile.path.endsWith("generated/kayan/schema/kayan.schema.json"),
        )
        assertTrue(
            extension.markdownSchemaOutputFile.get().asFile.path.endsWith("generated/kayan/schema/SCHEMA.md"),
        )
    }

    @Test
    fun applyRegistersSchemaExportAndDefaultGenerationTasks() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply(KayanConfigPlugin::class.java)

        val exportTask = project.tasks.named("exportKayanSchema", ExportKayanSchemaTask::class.java).get()
        val generateTask = project.tasks.named("generateKayanConfig", GenerateKayanConfigTask::class.java).get()
        assertEquals("documentation", exportTask.group)
        assertEquals("code generation", generateTask.group)
        assertNotNull(exportTask.jsonSchemaOutputFile.orNull)
        assertNotNull(generateTask.outputDir.orNull)
        assertTrue(exportTask in generateTask.taskDependencies.getDependencies(generateTask))
    }

    @Test
    fun childProjectApplyDoesNotCreateRootExtensionLocally() {
        val root = ProjectBuilder.builder().withName("root").build()
        val child = ProjectBuilder.builder().withName("child").withParent(root).build()

        root.plugins.apply(KayanConfigPlugin::class.java)
        child.plugins.apply(KayanConfigPlugin::class.java)

        assertNotNull(root.extensions.findByType(KayanRootExtension::class.java))
        assertEquals(null, child.extensions.findByType(KayanRootExtension::class.java))
        assertNotNull(child.extensions.findByType(KayanExtension::class.java))
    }
}
