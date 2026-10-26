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
import kotlin.test.assertFailsWith

class ExplainKayanConfigTaskTest {
    @Test
    fun reportsTypedParseAndTypeErrorsWithSourceContext() {
        val task = task("{")
        assertMessageContains(assertFailsWith<GradleException> { task.explain() }, "default.json", "JSON")

        task.baseConfigFile.get().asFile.writeText("""{"value":false,"flavors":{"prod":{}}}""")
        assertMessageContains(assertFailsWith<GradleException> { task.explain() }, "default.json", "$.value", "string")
    }

    @Test
    fun validatesAllFlavorsRequiredKeysAndProtectedCustomTargetOverrides() {
        val task = task("""{"flavors":{"prod":{"value":"ok"},"other":{}}}""")
        task.schemaEntries.set(listOf(entry(required = true).serialize()))
        assertMessageContains(
            assertFailsWith<GradleException> { task.explain() },
            "other",
            "value",
            "required",
        )

        task.baseConfigFile.get().asFile.writeText("""{"targets":{"jvm":{}},"flavors":{"prod":{}}}""")
        task.schemaEntries.set(listOf(entry(preventOverride = true).serialize()))
        task.customConfigFile.set(
            File(task.baseConfigFile.get().asFile.parentFile, "custom.json").apply {
                writeText("""{"flavors":{"prod":{"targets":{"jvm":{"value":"blocked"}}}}}""")
            },
        )
        assertMessageContains(
            assertFailsWith<GradleException> { task.explain() },
            "custom.json",
            "$.flavors.prod.targets.jvm.value",
            "preventOverride",
        )
    }

    @Test
    fun honorsValidationModeAndRejectsUnknownFlavorAndBlankSelectors() {
        val task = task("""{"ignored":"shared","value":"ok","flavors":{"prod":{}}}""")
        task.explain()
        task.validationMode.set(KayanValidationMode.STRICT)
        assertMessageContains(assertFailsWith<GradleException> { task.explain() }, "ignored", "default.json")
        task.validationMode.set(KayanValidationMode.SUBSET)
        task.selectFlavor("unknown")
        assertMessageContains(assertFailsWith<GradleException> { task.explain() }, "unknown")
        task.selectFlavor(" ")
        assertMessageContains(assertFailsWith<GradleException> { task.explain() }, "flavor")
        task.selectFlavor("prod")
        task.selectTarget(" ")
        assertMessageContains(assertFailsWith<GradleException> { task.explain() }, "target")
    }

    private fun task(base: String): ExplainKayanConfigTask {
        val dir = createTempDirectory(prefix = "kayan-explain-task-test").toFile()
        val project = ProjectBuilder.builder().withProjectDir(dir).build()
        val file = File(dir, "default.json").apply { writeText(base) }
        return project.tasks.register("explainKayanConfig", ExplainKayanConfigTask::class.java) {
            it.flavor.set("prod")
            it.baseConfigFile.set(file)
            it.configFormat.set(ConfigFormat.JSON)
            it.validationMode.set(KayanValidationMode.SUBSET)
            it.schemaEntries.set(listOf(entry().serialize()))
        }.get()
    }

    private fun entry(required: Boolean = false, preventOverride: Boolean = false): KayanSchemaEntrySpec =
        KayanSchemaEntrySpec(
            jsonKey = "value",
            propertyName = "VALUE",
            kind = ConfigValueKind.STRING,
            required = required,
            nullable = false,
            preventOverride = preventOverride,
        )
}
