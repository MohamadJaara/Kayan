package io.kayan.gradle

import io.kayan.ConfigValueKind
import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExportKayanSchemaTaskTest {
    @Test
    fun exportWritesJsonAndMarkdownSchemaArtifacts() {
        val projectDir = createTempDirectory(prefix = "kayan-export-task-test").toFile()
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val jsonSchemaFile = File(projectDir, "build/generated/schema/nested/kayan.schema.json")
        val markdownSchemaFile = File(projectDir, "build/generated/schema/nested/SCHEMA.md")
        val task = project.tasks.register("exportKayanSchema", ExportKayanSchemaTask::class.java) {
            it.schemaEntries.set(listOf(bundleIdEntry().serialize()))
            it.packageName.set(" sample.config ")
            it.className.set(" KayanConfig ")
            it.jsonSchemaOutputFile.set(jsonSchemaFile)
            it.markdownSchemaOutputFile.set(markdownSchemaFile)
        }.get()

        task.export()

        assertTrue(jsonSchemaFile.exists())
        assertTrue(markdownSchemaFile.exists())
        assertTrue(jsonSchemaFile.readText().contains("\"bundle_id\""))
        assertTrue(markdownSchemaFile.readText().contains("`sample.config.KayanConfig`"))
    }

    @Test
    fun exportRejectsInvalidGeneratedClassName() {
        val projectDir = createTempDirectory(prefix = "kayan-export-task-test").toFile()
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val task = project.tasks.register("exportKayanSchema", ExportKayanSchemaTask::class.java) {
            it.schemaEntries.set(listOf(bundleIdEntry().serialize()))
            it.className.set("class")
            it.jsonSchemaOutputFile.set(File(projectDir, "kayan.schema.json"))
            it.markdownSchemaOutputFile.set(File(projectDir, "SCHEMA.md"))
        }.get()

        val error = assertFailsWith<GradleException> {
            task.export()
        }

        assertMessageContains(error, "className 'class' must be a valid Kotlin identifier")
    }

    private fun bundleIdEntry(): KayanSchemaEntrySpec = KayanSchemaEntrySpec(
        jsonKey = "bundle_id",
        propertyName = "BUNDLE_ID",
        kind = ConfigValueKind.STRING,
        required = true,
        nullable = false,
    )
}
