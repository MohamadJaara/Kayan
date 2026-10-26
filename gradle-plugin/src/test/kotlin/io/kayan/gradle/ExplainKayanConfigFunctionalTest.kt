package io.kayan.gradle

import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplainKayanConfigFunctionalTest {
    @Test
    fun taskOptionsOverrideExtensionFlavorAndReadUpdatedInputsWithConfigurationCacheReuse() {
        val dir = project(
            """
                kayan {
                    flavor.set("prod")
                    customConfigFile.set(layout.projectDirectory.file("custom.json"))
                    schema {
                        string("value", "VALUE", required = true)
                        string("nullable", "NULLABLE", nullable = true)
                        string("absent", "ABSENT")
                    }
                }
            """.trimIndent(),
        )
        File(dir, "default.json").writeText(
            """
                {
                  "value": "base-root",
                  "nullable": "lower-value",
                  "targets": {"jvm": {"value": "base-root-target"}},
                  "flavors": {
                    "prod": {"value": "production"},
                    "dev": {"value": "base-dev", "targets": {"jvm": {"value": "base-dev-target"}}}
                  }
                }
            """.trimIndent(),
        )
        val custom = File(dir, "custom.json").apply {
            writeText(
                """
                    {
                      "value": "custom-root",
                      "targets": {"jvm": {"value": "custom-root-target"}},
                      "flavors": {
                        "dev": {"value": "custom-dev", "targets": {"jvm": {"value": "winner", "nullable": null}}}
                      }
                    }
                """.trimIndent(),
            )
        }
        val arguments = arrayOf("explainKayanConfig", "--flavor", "dev", "--target", "jvm", "--configuration-cache")

        val first = kayanGradleRunner(dir, *arguments).build()
        val second = kayanGradleRunner(dir, *arguments).build()

        assertEquals(TaskOutcome.SUCCESS, first.task(":explainKayanConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, second.task(":explainKayanConfig")?.outcome)
        assertEquals(listOf(":explainKayanConfig"), second.tasks.map { it.path })
        assertTrue(second.output.contains("Configuration cache entry reused."))
        assertEquals(explanation(first.output), explanation(second.output))
        assertTrue(second.output.contains("Kayan configuration: flavor=\"dev\", target=\"jvm\""))
        assertTrue(second.output.contains("\"value\" = \"winner\""))
        assertTrue(second.output.contains("layer: custom flavor target overlay"))
        assertTrue(second.output.contains("path: \"$.flavors.dev.targets.jvm.value\""))
        assertTrue(second.output.contains("\"nullable\" = null\n  source:"))
        assertTrue(second.output.contains("\"absent\" = <absent>\n  source: <none>"))

        custom.writeText(custom.readText().replace("winner", "updated"))
        val changed = kayanGradleRunner(dir, *arguments).build()
        assertTrue(changed.output.contains("Configuration cache entry reused."))
        assertTrue(changed.output.contains("\"value\" = \"updated\""))
        assertFalse(File(dir, "build/generated").exists())
    }

    @Test
    fun inheritsRootFilesFormatSchemaAndDefaultFlavorWithoutKotlinOrGenerationSettings() {
        val dir = project(
            """
                kayanRoot {
                    flavor.set("prod")
                    configFormat.set(io.kayan.ConfigFormat.AUTO)
                    baseConfigFile.set(layout.projectDirectory.file("base.yaml"))
                    customConfigFile.set(layout.projectDirectory.file("custom.yaml"))
                    schema {
                        string("value", "VALUE", required = true)
                    }
                }
            """.trimIndent(),
        )
        File(dir, "settings.gradle.kts").appendText("\ninclude(\":feature\")\n")
        val child = File(dir, "feature").apply { mkdirs() }
        File(child, "build.gradle.kts").writeText(
            """
                plugins { id("io.github.mohamadjaara.kayan") }
                kayan {
                    inheritFromRoot()
                    className.unset()
                    schema { include("value") }
                }
            """.trimIndent(),
        )
        File(dir, "base.yaml").writeText(
            """
                value: base-root
                ignored_shared_key: ignored
                flavors:
                  prod:
                    value: base-flavor
            """.trimIndent(),
        )
        File(dir, "custom.yaml").writeText(
            """
                value: custom-root
                flavors: {}
            """.trimIndent(),
        )

        val result = kayanGradleRunner(dir, ":feature:explainKayanConfig").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":feature:explainKayanConfig")?.outcome)
        assertTrue(result.output.contains("flavor=\"prod\", target=<none>"))
        assertTrue(result.output.contains("\"value\" = \"custom-root\""))
        assertTrue(result.output.contains("custom.yaml"))
        assertTrue(result.output.contains("layer: custom root defaults"))
        assertTrue(result.output.contains("path: \"$.value\""))
        assertFalse(File(child, "build/generated").exists())
    }

    @Test
    fun unrelatedTaskDoesNotResolveMissingConfigOrSchema() {
        val dir = project("")

        val result = kayanGradleRunner(dir, "help").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":help")?.outcome)
        assertEquals(null, result.task(":explainKayanConfig"))
    }

    private fun project(configuration: String): File = createTempDirectory(prefix = "kayan-explain-functional").toFile()
        .apply {
            File(this, "settings.gradle.kts").writeText("rootProject.name = \"explain-test\"\n")
            File(this, "build.gradle.kts").writeText(
                "plugins { id(\"io.github.mohamadjaara.kayan\") }\n$configuration\n",
            )
        }

    private fun explanation(output: String): String = output.lines()
        .dropWhile { !it.startsWith("Kayan configuration:") }
        .takeWhile { it.isNotBlank() }
        .joinToString("\n")
}
