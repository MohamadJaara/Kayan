package io.kayan.gradle

import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KayanJsonSyntaxFunctionalTest {
    @Test
    fun rejectsMalformedJsonLazilyAndRereadsCorrectedInputWithConfigurationCache() {
        val projectDir = createTempDirectory(prefix = "kayan-json-syntax-test").toFile()
        File(projectDir, "settings.gradle.kts").writeText(
            """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }
                rootProject.name = "json-syntax-test"
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
                    baseConfigFile.set(layout.projectDirectory.file("malformed.json"))
                    schema {
                        string("value", "VALUE", required = true)
                    }
                }
            """.trimIndent(),
        )
        val configFile = File(projectDir, "malformed.json")
        configFile.writeText("""{"flavors":{"prod":{}},"value":bareword}""")

        val help = kayanGradleRunner(projectDir, "help", "--configuration-cache").build()
        assertEquals(TaskOutcome.SUCCESS, help.task(":help")?.outcome)

        val first = kayanGradleRunner(projectDir, "generateKayanConfig", "--configuration-cache").buildAndFail()
        assertEquals(TaskOutcome.FAILED, first.task(":generateKayanConfig")?.outcome)
        assertTrue(first.output.contains("Invalid JSON in source '"))
        assertTrue(first.output.contains("malformed.json"))
        assertTrue(first.output.contains("Configuration cache entry stored."))

        configFile.writeText("""{"flavors":{"prod":{}},"value":"valid","ignored":01}""")
        val second = kayanGradleRunner(projectDir, "generateKayanConfig", "--configuration-cache").buildAndFail()
        assertEquals(TaskOutcome.FAILED, second.task(":generateKayanConfig")?.outcome)
        assertTrue(second.output.contains("Invalid JSON in source '"))
        assertTrue(second.output.contains("malformed.json"))
        assertTrue(second.output.contains("Configuration cache entry reused."))
        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
        assertFalse(generatedFile.exists())

        configFile.writeText("""{"flavors":{"prod":{}},"value":"quoted"}""")
        val corrected = kayanGradleRunner(projectDir, "generateKayanConfig", "--configuration-cache").build()
        assertEquals(TaskOutcome.SUCCESS, corrected.task(":generateKayanConfig")?.outcome)
        assertTrue(corrected.output.contains("Configuration cache entry reused."))
        assertTrue(generatedFile.readText().contains("public const val VALUE: String = \"quoted\""))
    }
}
