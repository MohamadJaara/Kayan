package io.kayan.gradle

import io.kayan.assertMessageContains
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalKayanGradleApi::class)
class KayanBuildValueFunctionalTest {
    @Test
    fun readsResolvedBuildValueInsideBuildScript() {
        val projectDir = createProject(
            buildScript = buildScript(
                """
                    abstract class PrintBuildValueTask : DefaultTask() {
                        @get:Input
                        abstract val value: Property<String>

                        @TaskAction
                        fun printValue() {
                            println("brand=${'$'}{value.get()}")
                        }
                    }

                    tasks.register<PrintBuildValueTask>("printBuildValue") {
                        value.set(kayan.buildValue("brand_name").asStringProvider())
                    }
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "brand_name": "Example"
                }
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "printBuildValue").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printBuildValue")?.outcome)
        assertTrue(result.output.contains("brand=Example"))
    }

    @Test
    fun readsJvmTargetSpecificBrandNameInsideBuildScript() {
        val projectDir = createProject(
            buildScript = buildScript(
                """
                    abstract class PrintBuildValueTask : DefaultTask() {
                        @get:Input
                        abstract val value: Property<String>

                        @TaskAction
                        fun printValue() {
                            println("brand=${'$'}{value.get()}")
                        }
                    }

                    tasks.register<PrintBuildValueTask>("printBuildValue") {
                        value.set(kayan.buildValue("brand_name", "jvm").asStringProvider())
                    }
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "brand_name": "Example",
                      "targets": {
                        "jvm": {
                          "brand_name": "Example JVM"
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "printBuildValue").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printBuildValue")?.outcome)
        assertTrue(result.output.contains("brand=Example JVM"))
    }

    @Test
    fun defaultSubsetValidationModeAllowsSharedUnknownKeysForBuildValues() {
        val projectDir = createProject(
            buildScript = buildScript(
                """
                    abstract class PrintBuildValueTask : DefaultTask() {
                        @get:Input
                        abstract val value: Property<String>

                        @TaskAction
                        fun printValue() {
                            println("brand=${'$'}{value.get()}")
                        }
                    }

                    tasks.register<PrintBuildValueTask>("printBuildValue") {
                        value.set(kayan.buildValue("brand_name", "jvm").asStringProvider())
                    }
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "unknown_root_key": true,
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "brand_name": "Example",
                      "targets": {
                        "jvm": {
                          "brand_name": "Example JVM",
                          "unknown_target_key": "ignored"
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "printBuildValue").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printBuildValue")?.outcome)
        assertTrue(result.output.contains("brand=Example JVM"))
    }

    @Test
    fun conditionallyAddsDependencyFromBooleanBuildValue() {
        val projectDir = createProject(
            buildScript = buildScript(
                """
                    val isSearchEnabled = kayan.buildValue("feature_search_enabled").asBoolean()

                    dependencies {
                        if (isSearchEnabled) {
                            implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
                        }
                    }

                    tasks.register("printImplementationDependencies") {
                        doLast {
                            val implementation = configurations.getByName("implementation")
                            println(
                                "implementationDependencies=" + implementation.dependencies.joinToString {
                                    "${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}"
                                },
                            )
                        }
                    }
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "feature_search_enabled": true
                }
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "printImplementationDependencies").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printImplementationDependencies")?.outcome)
        assertTrue(result.output.contains("implementationDependencies=org.jetbrains.kotlin:kotlin-stdlib:2.3.20"))
    }

    @Test
    fun typeMismatchFailsWithClearError() {
        val projectDir = createProject(
            buildScript = buildScript(
                """
                    val enabled = kayan.buildValue("brand_name").asBoolean()

                    tasks.register("printBrokenValue") {
                        doLast {
                            println(enabled)
                        }
                    }
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "brand_name": "Example"
                }
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "printBrokenValue").buildAndFail()

        assertTrue(result.output.contains("Key 'brand_name' is STRING, cannot access as Boolean"))
    }

    @Test
    fun unknownKeyFailsWithSuggestions() {
        val projectDir = createProject(
            buildScript = buildScript(
                """
                    val brandName = kayan.buildValue("brand_nam").asString()

                    tasks.register("printBuildValue") {
                        doLast {
                            println(brandName)
                        }
                    }
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "brand_name": "Example"
                }
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "printBuildValue").buildAndFail()

        assertMessageContains(
            IllegalStateException(result.output),
            "Key 'brand_nam' is not defined in the Kayan schema.",
            "Did you mean 'brand_name'?",
        )
    }

    @Test
    fun customConfigOverridesResolvedBuildValue() {
        val projectDir = createProject(
            buildScript = buildScript(
                """
                    abstract class PrintBuildValueTask : DefaultTask() {
                        @get:Input
                        abstract val value: Property<String>

                        @TaskAction
                        fun printValue() {
                            println("brand=${'$'}{value.get()}")
                        }
                    }

                    tasks.register<PrintBuildValueTask>("printBuildValue") {
                        value.set(kayan.buildValue("brand_name").asStringProvider())
                    }
                """.trimIndent(),
                """
                    customConfigFile.set(layout.projectDirectory.file("custom-overrides.json"))
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "brand_name": "Example"
                }
            """.trimIndent(),
            customJson = """
                {
                  "flavors": {},
                  "brand_name": "Custom Example"
                }
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "printBuildValue").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printBuildValue")?.outcome)
        assertTrue(result.output.contains("brand=Custom Example"))
    }

    @Test
    fun readsResolvedBuildValueFromYmlConfig() {
        val projectDir = createProject(
            buildScript = buildScript(
                """
                    abstract class PrintBuildValueTask : DefaultTask() {
                        @get:Input
                        abstract val value: Property<String>

                        @TaskAction
                        fun printValue() {
                            println("brand=${'$'}{value.get()}")
                        }
                    }

                    tasks.register<PrintBuildValueTask>("printBuildValue") {
                        value.set(kayan.buildValue("brand_name").asStringProvider())
                    }
                """.trimIndent(),
                """
                    baseConfigFile.set(layout.projectDirectory.file("default.yml"))
                    configFormat.set(io.kayan.ConfigFormat.YAML)
                """.trimIndent(),
            ),
            baseJson = """
                flavors:
                  prod:
                    bundle_id: com.example.prod
                brand_name: Example
            """.trimIndent(),
            baseFileName = "default.yml",
        )

        val result = gradleRunner(projectDir, "printBuildValue").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printBuildValue")?.outcome)
        assertTrue(result.output.contains("brand=Example"))
    }

    @Test
    fun reusesConfigurationCacheAndInvalidatesWhenConfigChanges() {
        val projectDir = createProject(
            buildScript = buildScript(
                """
                    abstract class PrintBuildValueTask : DefaultTask() {
                        @get:Input
                        abstract val value: Property<String>

                        @TaskAction
                        fun printValue() {
                            println("brand=${'$'}{value.get()}")
                        }
                    }

                    tasks.register<PrintBuildValueTask>("printBuildValue") {
                        value.set(kayan.buildValue("brand_name").asStringProvider())
                    }
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "brand_name": "Example"
                }
            """.trimIndent(),
        )

        val firstRun = gradleRunner(projectDir, "printBuildValue", "--configuration-cache").build()
        val secondRun = gradleRunner(projectDir, "printBuildValue", "--configuration-cache").build()

        File(projectDir, "default.json").writeText(
            """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "brand_name": "Updated Example"
                }
            """.trimIndent(),
        )

        val thirdRun = gradleRunner(projectDir, "printBuildValue", "--configuration-cache").build()

        assertTrue(firstRun.output.contains("Configuration cache entry stored."))
        assertTrue(secondRun.output.contains("Configuration cache entry reused."))
        assertTrue(thirdRun.output.contains("brand=Updated Example"))
    }

    @Test
    fun configurationCacheDoesNotPersistUnrequestedResolvedValues() {
        val secret = "secret-prod-token"
        val projectDir = createProject(
            buildScript = """
                import org.gradle.api.DefaultTask
                import org.gradle.api.provider.Property
                import org.gradle.api.tasks.Input
                import org.gradle.api.tasks.TaskAction

                plugins {
                    kotlin("jvm") version "2.3.20"
                    id("io.github.mohamadjaara.kayan")
                }

                repositories {
                    google()
                    mavenCentral()
                }

                kayan {
                    packageName.set("sample.config")
                    flavor.set("prod")
                    baseConfigFile.set(layout.projectDirectory.file("default.json"))

                    schema {
                        string("bundle_id", "BUNDLE_ID", required = true)
                        string("brand_name", "BRAND_NAME")
                        string("api_secret", "API_SECRET")
                    }
                }

                abstract class PrintBuildValueTask : DefaultTask() {
                    @get:Input
                    abstract val value: Property<String>

                    @TaskAction
                    fun printValue() {
                        println("brand=${'$'}{value.get()}")
                    }
                }

                tasks.register<PrintBuildValueTask>("printBuildValue") {
                    value.set(kayan.buildValue("brand_name").asStringProvider())
                }
            """.trimIndent(),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "api_secret": "$secret"
                    }
                  },
                  "brand_name": "Example"
                }
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "printBuildValue", "--configuration-cache").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printBuildValue")?.outcome)
        assertTrue(result.output.contains("Configuration cache entry stored."))
        assertFalse(configurationCacheContains(projectDir, secret))
        assertFalse(configurationCacheContains(projectDir, "api_secret"))
    }

    private fun createProject(
        buildScript: String,
        baseJson: String,
        customJson: String? = null,
        baseFileName: String = "default.json",
        customFileName: String = "custom-overrides.json",
    ): File {
        val projectDir = createTempDirectory(prefix = "kayan-build-value-functional-test").toFile()
        File(projectDir, "settings.gradle.kts").writeText(
            """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                    }
                }

                dependencyResolutionManagement {
                    repositories {
                        google()
                        mavenCentral()
                    }
                }

                rootProject.name = "sample"
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(buildScript)
        File(projectDir, baseFileName).writeText(baseJson)
        customJson?.let { File(projectDir, customFileName).writeText(it) }

        return projectDir
    }

    private fun buildScript(
        buildLogic: String,
        kayanConfiguration: String = "",
    ): String = """
        import org.gradle.api.DefaultTask
        import org.gradle.api.provider.Property
        import org.gradle.api.tasks.Input
        import org.gradle.api.tasks.TaskAction

        plugins {
            kotlin("jvm") version "2.3.20"
            id("io.github.mohamadjaara.kayan")
        }

        repositories {
            google()
            mavenCentral()
        }

        kayan {
            packageName.set("sample.config")
            flavor.set("prod")
            baseConfigFile.set(layout.projectDirectory.file("default.json"))

            schema {
                string("bundle_id", "BUNDLE_ID", required = true)
                string("brand_name", "BRAND_NAME")
                boolean("feature_search_enabled", "FEATURE_SEARCH_ENABLED")
            }

            $kayanConfiguration
        }

        $buildLogic
    """.trimIndent()

    private fun gradleRunner(projectDir: File, vararg tasks: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*tasks, "--stacktrace")

    private fun configurationCacheContains(projectDir: File, needle: String): Boolean {
        val cacheDir = File(projectDir, ".gradle/configuration-cache")
        if (!cacheDir.exists()) {
            return false
        }

        val needleBytes = needle.toByteArray(StandardCharsets.UTF_8)
        return cacheDir.walkTopDown()
            .filter(File::isFile)
            .any { file -> fileContainsBytes(file, needleBytes) }
    }

    private fun fileContainsBytes(file: File, needle: ByteArray): Boolean =
        runCatching {
            val bytes = file.readBytes()
            if (needle.isEmpty() || bytes.size < needle.size) {
                return@runCatching false
            }

            for (start in 0..bytes.size - needle.size) {
                var matches = true
                for (index in needle.indices) {
                    if (bytes[start + index] != needle[index]) {
                        matches = false
                        break
                    }
                }
                if (matches) {
                    return@runCatching true
                }
            }

            false
        }.getOrDefault(false)
}
