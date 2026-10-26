package io.kayan.gradle

import io.kayan.ConfigFormat
import io.kayan.ConfigValueKind
import io.kayan.KayanValidationMode
import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateKayanConfigTaskTest {
    @Test
    fun registersLazilyWithoutReadingConfigurationFilesOrSchema() {
        val project = ProjectBuilder.builder().build()
        var realized = false
        project.tasks.withType(ValidateKayanConfigTask::class.java).configureEach { realized = true }

        project.plugins.apply(KayanConfigPlugin::class.java)

        assertTrue("validateKayanConfig" in project.tasks.names)
        assertFalse(realized)
        val task = project.tasks.named("validateKayanConfig", ValidateKayanConfigTask::class.java).get()
        assertEquals("verification", task.group)
        assertTrue(task.outputs.files.isEmpty)
        assertTrue(task.taskDependencies.getDependencies(task).isEmpty())
    }

    @Test
    fun wiresCheckTasksRegisteredBeforeOrAfterPluginApplication() {
        listOf(true, false).forEach { checkFirst ->
            val project = ProjectBuilder.builder().build()
            if (checkFirst) {
                project.tasks.register("check")
            }
            project.plugins.apply(KayanConfigPlugin::class.java)
            if (!checkFirst) {
                project.tasks.register("check")
            }

            val check = project.tasks.named("check").get()
            assertTrue(check.taskDependencies.getDependencies(check).any { it.name == "validateKayanConfig" })
        }
    }

    @Test
    fun reportsMissingSchemaAndInvalidSchemaWithExistingDiagnostics() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(KayanConfigPlugin::class.java)
        val task = project.tasks.named("validateKayanConfig", ValidateKayanConfigTask::class.java).get()

        assertMessageContains(assertFailsWith<GradleException> { task.validate() }, "consumer-defined schema entry")

        task.schemaEntries.set(
            listOf(
                KayanSchemaEntrySpec(
                    jsonKey = "api_url",
                    propertyName = "invalid-name",
                    kind = ConfigValueKind.STRING,
                    required = false,
                    nullable = false,
                ).serialize(),
            ),
        )
        assertMessageContains(assertFailsWith<GradleException> { task.validate() }, "Failed to build Kayan schema")
    }

    @Test
    fun reportsMissingFilesWithTypedBoundaryDiagnostics() {
        val directory = createTempDirectory(prefix = "kayan-validation-task-test").toFile()
        val project = ProjectBuilder.builder().withProjectDir(directory).build()
        project.plugins.apply(KayanConfigPlugin::class.java)
        project.extensions.getByType(KayanExtension::class.java).schema { string("api_url", "API_URL") }
        val task = project.tasks.named("validateKayanConfig", ValidateKayanConfigTask::class.java).get()

        assertMessageContains(assertFailsWith<GradleException> { task.validate() }, "base config file does not exist")

        File(directory, "default.json").writeText("""{"flavors":{"prod":{}}}""")
        task.customConfigFile.set(File(directory, "missing.json"))
        assertMessageContains(assertFailsWith<GradleException> { task.validate() }, "custom config file does not exist")
    }

    @Test
    fun explicitRootConsumerSchemaKeepsItsOwnFilesFormatAndValidationMode() {
        val directory = createTempDirectory(prefix = "kayan-root-consumer-validation-test").toFile()
        val project = ProjectBuilder.builder().withProjectDir(directory).build()
        project.plugins.apply(KayanConfigPlugin::class.java)
        project.extensions.getByType(KayanRootExtension::class.java).apply {
            baseConfigFile.set(File(directory, "shared.yaml"))
            customConfigFile.set(File(directory, "shared-custom.yaml"))
            configFormat.set(ConfigFormat.YAML)
            validationMode.set(KayanValidationMode.STRICT)
            schema { boolean("shared", "SHARED", required = true) }
        }
        val base = File(directory, "local.json").apply {
            writeText("""{"api_url":"local","flavors":{"prod":{}}}""")
        }
        val custom = File(directory, "custom.json").apply { writeText("""{"unknown":true,"flavors":{}}""") }
        val extension = project.extensions.getByType(KayanExtension::class.java).apply {
            baseConfigFile.set(base)
            customConfigFile.set(custom)
            configFormat.set(ConfigFormat.JSON)
            validationMode.set(KayanValidationMode.SUBSET)
            schema { string("api_url", "API_URL", required = true) }
        }
        val task = project.tasks.named("validateKayanConfig", ValidateKayanConfigTask::class.java).get()

        assertEquals(extension.serializedSchemaEntries(), task.schemaEntries.get())
        assertEquals(base, task.baseConfigFile.get().asFile)
        assertEquals(custom, task.customConfigFile.get().asFile)
        assertEquals(ConfigFormat.JSON, task.configFormat.get())
        assertEquals(KayanValidationMode.SUBSET, task.validationMode.get())
        task.validate()
    }
}
