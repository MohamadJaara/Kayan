package io.kayan.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidateKayanConfigFunctionalTest {
    @Test
    fun validatesJsonWithoutKotlinPluginOrGenerationSettingsAndReusesConfigurationCache() {
        val directory = project(
            config = """{"api_url":"base","flavors":{"prod":{},"staging":{}}}""",
            configuration = """
                flavor.set("prod")
                className.set("")
                customConfigFile.set(layout.projectDirectory.file("custom.json"))
            """.trimIndent(),
        )
        File(directory, "custom.json").writeText("""{"flavors":{"prod":{"api_url":"custom"}}}""")

        val first = kayanGradleRunner(directory, "validateKayanConfig", "--configuration-cache").build()
        val second = kayanGradleRunner(directory, "validateKayanConfig", "--configuration-cache").build()

        assertValidationOnly(first, directory)
        assertValidationOnly(second, directory)
        assertTrue(second.output.contains("Reusing configuration cache."))
        assertTrue(second.output.contains("Kayan configuration is valid: 2 flavor(s)"))

        File(directory, "custom.json").writeText("""{"flavors":{"staging":{"api_url":42}}}""")
        val invalidCustom = kayanGradleRunner(directory, "validateKayanConfig", "--configuration-cache").buildAndFail()
        assertTrue(invalidCustom.output.contains("Reusing configuration cache."))
        assertTrue(invalidCustom.output.contains("custom.json"))
        assertTrue(invalidCustom.output.contains("staging"))
        assertTrue(invalidCustom.output.contains("expected string"))

        File(directory, "custom.json").writeText("""{"flavors":{}}""")
        File(directory, "default.json").writeText(
            """{"flavors":{"prod":{"api_url":"prod"},"staging":{}}}""",
        )
        val invalidBase = kayanGradleRunner(directory, "validateKayanConfig", "--configuration-cache").buildAndFail()
        assertTrue(invalidBase.output.contains("Reusing configuration cache."))
        assertTrue(invalidBase.output.contains("staging"))
        assertTrue(invalidBase.output.contains("api_url"))
    }

    @Test
    fun validatesYamlAndCustomOverlaysWithoutGenerationMappings() {
        val directory = project(
            config = """
                targets:
                  jvm: {}
                  ios: {}
                flavors:
                  prod: {}
                  staging: {}
            """.trimIndent(),
            configuration = """
                baseConfigFile.set(layout.projectDirectory.file("base.yaml"))
                customConfigFile.set(layout.projectDirectory.file("custom.yml"))
                configFormat.set(io.kayan.ConfigFormat.AUTO)
            """.trimIndent(),
            fileName = "base.yaml",
        )
        File(directory, "custom.yml").writeText(
            """
                targets:
                  jvm:
                    api_url: jvm-custom
                  ios:
                    api_url: ios-custom
                flavors: {}
            """.trimIndent(),
        )

        val result = kayanGradleRunner(directory, "validateKayanConfig").build()

        assertValidationOnly(result, directory)
        assertTrue(result.output.contains("2 flavor(s) for targets 'jvm', 'ios'"))
    }

    @Test
    fun checksEveryConfiguredTargetWithoutRequiringBaseOnlyValues() {
        val directory = project(
            config = """
                {"targets":{"jvm":{"api_url":"jvm"},"ios":{"api_url":"ios"}},
                 "flavors":{"prod":{},"staging":{}}}
            """.trimIndent(),
            configuration = """
                flavor.set("prod")
                targets("jvm", "ios")
            """.trimIndent(),
            plugins = """
                kotlin("multiplatform") version "2.3.21"
                id("io.github.mohamadjaara.kayan")
            """.trimIndent(),
            setup = """
                repositories { mavenCentral() }
                kotlin {
                    jvm()
                    sourceSets.create("iosMain")
                }
            """.trimIndent(),
        )

        val first = kayanGradleRunner(directory, "validateKayanConfig", "--configuration-cache").build()
        val second = kayanGradleRunner(directory, "validateKayanConfig", "--configuration-cache").build()
        assertValidationOnly(first, directory)
        assertValidationOnly(second, directory)
        assertTrue(second.output.contains("Reusing configuration cache."))

        File(directory, "default.json").writeText(
            """
                {"targets":{"jvm":{"api_url":"jvm"}},"flavors":{
                 "prod":{"targets":{"ios":{"api_url":"ios"}}},"staging":{}}}
            """.trimIndent(),
        )
        val failed = kayanGradleRunner(directory, "validateKayanConfig", "--configuration-cache").buildAndFail()
        assertTrue(failed.output.contains("Reusing configuration cache."))
        assertTrue(failed.output.contains("staging"))
        assertTrue(failed.output.contains("ios"))
        assertTrue(failed.output.contains("api_url"))
    }

    @Test
    fun checkLifecycleRunsValidationWithEitherPluginOrder() {
        listOf(true, false).forEach { baseFirst ->
            val plugins = if (baseFirst) {
                """id("base"); id("io.github.mohamadjaara.kayan")"""
            } else {
                """id("io.github.mohamadjaara.kayan"); id("base")"""
            }
            val directory = project(
                config = """{"api_url":"base","flavors":{"prod":{}}}""",
                plugins = plugins,
            )

            val result = kayanGradleRunner(directory, "check", "--configuration-cache").build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
            assertValidationOnly(result, directory)
        }
    }

    @Test
    fun reportsMissingSchemaAndMissingConfigFiles() {
        val withoutSchema = project(config = """{"flavors":{"prod":{}}}""", schema = "")
        val schemaFailure = kayanGradleRunner(withoutSchema, "validateKayanConfig").buildAndFail()
        assertTrue(schemaFailure.output.contains("consumer-defined schema entry"))

        val missingBase = project(config = null)
        val baseFailure = kayanGradleRunner(missingBase, "validateKayanConfig").buildAndFail()
        assertTrue(baseFailure.output.contains("baseConfigFile"))
        assertTrue(baseFailure.output.contains("default.json"))

        val missingCustom = project(
            config = """{"api_url":"base","flavors":{"prod":{}}}""",
            configuration = """customConfigFile.set(layout.projectDirectory.file("missing.json"))""",
        )
        val customFailure = kayanGradleRunner(missingCustom, "validateKayanConfig").buildAndFail()
        assertTrue(customFailure.output.contains("customConfigFile"))
        assertTrue(customFailure.output.contains("missing.json"))
    }

    @Test
    fun rootCheckValidatesSharedConfigurationAndPreservesChildInputsWithConfigurationCache() {
        val directory = createTempDirectory(prefix = "kayan-shared-validation-test").toFile()
        File(directory, "settings.gradle.kts").writeText(
            """
                rootProject.name = "shared-validation-test"
                include(":feature")
            """.trimIndent(),
        )
        File(directory, "build.gradle.kts").writeText(
            """
                plugins {
                    base
                    id("io.github.mohamadjaara.kayan")
                }

                kayanRoot {
                    baseConfigFile.set(layout.projectDirectory.file("shared.yaml"))
                    customConfigFile.set(layout.projectDirectory.file("custom.yaml"))
                    configFormat.set(io.kayan.ConfigFormat.YAML)
                    validationMode.set(io.kayan.KayanValidationMode.STRICT)
                    schema {
                        string("api_url", "API_URL", required = true)
                        boolean("feature_enabled", "FEATURE_ENABLED", required = true)
                    }
                }
            """.trimIndent(),
        )
        File(directory, "shared.yaml").writeText(
            """
                api_url: shared
                feature_enabled: false
                flavors:
                  prod: {}
                  staging: {}
            """.trimIndent(),
        )
        File(directory, "custom.yaml").writeText(
            """
                feature_enabled: true
                flavors: {}
            """.trimIndent(),
        )
        val feature = File(directory, "feature").apply { mkdirs() }
        File(feature, "build.gradle.kts").writeText(
            """
                plugins {
                    base
                    id("io.github.mohamadjaara.kayan")
                }

                kayan {
                    inheritFromRoot()
                    baseConfigFile.set(layout.projectDirectory.file("local.json"))
                    customConfigFile.set(layout.projectDirectory.file("custom.json"))
                    configFormat.set(io.kayan.ConfigFormat.JSON)
                    validationMode.set(io.kayan.KayanValidationMode.SUBSET)
                    schema { include("api_url") }
                }
            """.trimIndent(),
        )
        File(feature, "local.json").writeText("""{"api_url":"local","flavors":{"prod":{}}}""")
        File(feature, "custom.json").writeText("""{"unknown":true,"flavors":{}}""")

        val first = kayanGradleRunner(directory, "check", "--configuration-cache").build()
        val second = kayanGradleRunner(directory, "check", "--configuration-cache").build()

        assertValidationOnly(first, directory)
        assertValidationOnly(second, directory)
        assertEquals(TaskOutcome.SUCCESS, second.task(":feature:validateKayanConfig")?.outcome)
        assertTrue(second.output.contains("Reusing configuration cache."))
        assertTrue(second.output.contains("2 flavor(s) without target overlays (STRICT)"))
        assertTrue(second.output.contains("1 flavor(s) without target overlays (SUBSET)"))
        assertFalse(File(feature, "build/generated").exists())

        File(directory, "custom.yaml").writeText("unknown: true\nflavors: {}\n")
        val failed = kayanGradleRunner(directory, "check", "--configuration-cache").buildAndFail()
        assertTrue(failed.output.contains("Reusing configuration cache."))
        assertTrue(failed.output.contains("custom.yaml"))
        assertTrue(failed.output.contains("unknown"))
    }

    private fun assertValidationOnly(result: BuildResult, directory: File) {
        assertEquals(TaskOutcome.SUCCESS, result.task(":validateKayanConfig")?.outcome)
        assertNull(result.task(":generateKayanConfig"))
        assertNull(result.task(":exportKayanSchema"))
        assertFalse(File(directory, "build/generated").exists())
    }

    private fun project(
        config: String?,
        configuration: String = "",
        schema: String = """string("api_url", "API_URL", required = true)""",
        fileName: String = "default.json",
        plugins: String = """id("io.github.mohamadjaara.kayan")""",
        setup: String = "",
    ): File {
        val directory = createTempDirectory(prefix = "kayan-validation-functional-test").toFile()
        File(directory, "settings.gradle.kts").writeText("""rootProject.name = "validation-test"""")
        File(directory, "build.gradle.kts").writeText(
            """
                @file:OptIn(io.kayan.gradle.ExperimentalKayanGenerationApi::class)

                plugins { $plugins }

                $setup

                kayan {
                    schema { $schema }
                    $configuration
                }
            """.trimIndent(),
        )
        config?.let { File(directory, fileName).writeText(it) }
        return directory
    }
}
