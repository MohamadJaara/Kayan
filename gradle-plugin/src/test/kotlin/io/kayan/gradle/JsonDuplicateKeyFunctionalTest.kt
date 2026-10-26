package io.kayan.gradle

import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonDuplicateKeyFunctionalTest {
    @Test
    fun rejectsDuplicateDefaultsOnlyWhenGenerationRuns() {
        val projectDir = createProject()
        File(projectDir, "default.json").writeText(
            """{"flavors":{"prod":{}},"value":"first","value":"second"}""",
        )

        val help = kayanGradleRunner(projectDir, "help", "--configuration-cache").build()
        assertEquals(TaskOutcome.SUCCESS, help.task(":help")?.outcome)

        val result = kayanGradleRunner(projectDir, "generateKayanConfig", "--configuration-cache").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":generateKayanConfig")?.outcome)
        assertTrue(
            result.output.contains("Invalid JSON in source '${File(projectDir, "default.json").canonicalPath}'"),
            result.output,
        )
        assertTrue(result.output.contains("Duplicate JSON object key 'value' at path '$.value'"))
    }

    @Test
    fun rejectsEscapedCustomMapDuplicatesWhenConfigurationCacheIsReused() {
        val projectDir = createProject()
        val customFile = File(projectDir, "custom-overrides.json")
        customFile.writeText("""{"flavors":{},"labels":{"en":"custom"}}""")

        val firstRun = kayanGradleRunner(projectDir, "generateKayanConfig", "--configuration-cache").build()
        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":generateKayanConfig")?.outcome)
        assertTrue(firstRun.output.contains("Configuration cache entry stored."))

        customFile.writeText(
            """{"flavors":{},"labels":{"en":"first","\u0065n":"second"}}""",
        )
        val secondRun = kayanGradleRunner(projectDir, "generateKayanConfig", "--configuration-cache").buildAndFail()

        assertEquals(TaskOutcome.FAILED, secondRun.task(":generateKayanConfig")?.outcome)
        assertTrue(secondRun.output.contains("Configuration cache entry reused."))
        assertTrue(secondRun.output.contains("Invalid JSON in source '${customFile.canonicalPath}'"), secondRun.output)
        assertTrue(secondRun.output.contains("Duplicate JSON object key 'en' at path '$.labels.en'"))
    }

    private fun createProject(): File {
        val projectDir = createTempDirectory(prefix = "kayan-json-duplicates").toFile()
        File(projectDir, "settings.gradle.kts").writeText(
            """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }
                rootProject.name = "json-duplicates"
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
                plugins {
                    kotlin("jvm") version "2.3.21"
                    id("io.github.mohamadjaara.kayan")
                }

                repositories {
                    mavenCentral()
                }

                kayan {
                    packageName.set("sample.config")
                    flavor.set("prod")
                    customConfigFile.set(layout.projectDirectory.file("custom-overrides.json"))
                    schema {
                        string("value", "VALUE", required = true)
                        stringMap("labels", "LABELS")
                    }
                }
            """.trimIndent(),
        )
        File(projectDir, "default.json").writeText(
            """{"flavors":{"prod":{}},"value":"base","labels":{"en":"base"}}""",
        )
        File(projectDir, "custom-overrides.json").writeText("""{"flavors":{}}""")
        return projectDir
    }
}
