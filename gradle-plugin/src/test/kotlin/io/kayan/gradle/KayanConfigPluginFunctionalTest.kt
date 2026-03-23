package io.kayan.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KayanConfigPluginFunctionalTest {
    @Test
    fun generatesConfigFromBaseFileAndCompilesCommonCode() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "feature_search_enabled": false
                    }
                  },
                  "brand_name": "Example"
                }
            """.trimIndent(),
            commonSource = """
                package sample

                import sample.config.KayanConfig

                val bundleId: String = KayanConfig.BUNDLE_ID
                val brandName: String? = KayanConfig.BRAND_NAME
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "compileKotlinJvm")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":exportKayanSchema")?.outcome)
        val generatedFile = File(projectDir, "build/generated/kayan/commonMain/kotlin/sample/config/KayanConfig.kt")
        val jsonSchemaFile = File(projectDir, "build/generated/kayan/schema/kayan.schema.json")
        val markdownSchemaFile = File(projectDir, "build/generated/kayan/schema/SCHEMA.md")
        assertTrue(generatedFile.exists())
        assertTrue(jsonSchemaFile.exists())
        assertTrue(markdownSchemaFile.exists())
        assertTrue(generatedFile.readText().contains("public object KayanConfig"))
        assertTrue(generatedFile.readText().contains("public const val BUNDLE_ID: String = \"com.example.prod\""))
        assertTrue(jsonSchemaFile.readText().contains("\"x-kayan-propertyName\": \"BUNDLE_ID\""))
        assertTrue(markdownSchemaFile.readText().contains("| `bundle_id` | `BUNDLE_ID` | `string` | Yes |"))
    }

    @Test
    fun generatesCollectionsAndAppliesCustomOverrides() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                    customConfigFile.set(layout.projectDirectory.file("custom-overrides.json"))
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "brand_name": "Base",
                  "regional_support_links": {
                    "example.com": ["sha-a"]
                  },
                  "support_links": ["https://example.com/help"]
                }
            """.trimIndent(),
            customJson = """
                {
                  "flavors": {
                    "prod": {
                      "brand_name": "Custom Flavor"
                    }
                  },
                  "support_links": ["https://custom.example.com/help"]
                }
            """.trimIndent(),
            commonSource = """
                package sample

                import sample.config.KayanConfig

                val links = KayanConfig.SUPPORT_LINKS
                val regionalLinks = KayanConfig.REGIONAL_SUPPORT_LINKS
            """.trimIndent(),
        )

        gradleRunner(projectDir, "compileKotlinJvm").build()

        val generatedFile = File(projectDir, "build/generated/kayan/commonMain/kotlin/sample/config/KayanConfig.kt")
        val generatedText = generatedFile.readText()
        assertTrue(
            generatedText.contains(
                "public val SUPPORT_LINKS: List<String> = " +
                    "listOf(\"https://custom.example.com/help\")",
            ),
        )
        assertTrue(
            generatedText.contains(
                "public val REGIONAL_SUPPORT_LINKS: Map<String, List<String>> = " +
                    "mapOf(\"example.com\" to listOf(\"sha-a\"))",
            ),
        )
        assertTrue(generatedText.contains("public val BRAND_NAME: String = \"Custom Flavor\""))
    }

    @Test
    fun rewritesExistingGeneratedFileWithoutCleaningOutputDirectory() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            commonSource = """
                package sample

                import sample.config.KayanConfig

                val bundleId: String = KayanConfig.BUNDLE_ID
            """.trimIndent(),
        )
        val generatedFile = File(projectDir, "build/generated/kayan/commonMain/kotlin/sample/config/KayanConfig.kt")
        generatedFile.parentFile.mkdirs()
        generatedFile.writeText("stale content")

        gradleRunner(projectDir, "generateKayanConfig").build()

        assertTrue(generatedFile.exists())
        assertTrue(generatedFile.readText().contains("public object KayanConfig"))
    }

    @Test
    fun generatesRicherBuiltInTypesAndSchemaDrivenNullability() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                    customConfigFile.set(layout.projectDirectory.file("custom-overrides.json"))
                """.trimIndent(),
                schemaBlock = """
                    long("max_cache_bytes", "MAX_CACHE_BYTES")
                    double("rollout_ratio", "ROLLOUT_RATIO")
                    stringMap("support_labels", "SUPPORT_LABELS")
                    enumValue("release_stage", "RELEASE_STAGE", "sample.ReleaseStage")
                    string("support_email", "SUPPORT_EMAIL", nullable = true)
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "release_stage": "beta"
                    }
                  },
                  "max_cache_bytes": 9876543210,
                  "support_labels": {
                    "channel": "stable"
                  },
                  "rollout_ratio": 0.75,
                  "support_email": "base@example.com"
                }
            """.trimIndent(),
            customJson = """
                {
                  "flavors": {
                    "prod": {
                      "support_email": null
                    }
                  }
                }
            """.trimIndent(),
            commonSource = """
                package sample

                import sample.config.KayanConfig

                enum class ReleaseStage {
                    BETA,
                    PROD,
                }

                val cacheBytes: Long = KayanConfig.MAX_CACHE_BYTES
                val rolloutRatio: Double = KayanConfig.ROLLOUT_RATIO
                val supportLabels: Map<String, String> = KayanConfig.SUPPORT_LABELS
                val releaseStage: ReleaseStage = KayanConfig.RELEASE_STAGE
                val supportEmail: String? = KayanConfig.SUPPORT_EMAIL
            """.trimIndent(),
        )

        gradleRunner(projectDir, "compileKotlinJvm").build()

        val generatedFile = File(projectDir, "build/generated/kayan/commonMain/kotlin/sample/config/KayanConfig.kt")
        val jsonSchemaFile = File(projectDir, "build/generated/kayan/schema/kayan.schema.json")
        val markdownSchemaFile = File(projectDir, "build/generated/kayan/schema/SCHEMA.md")
        val generatedText = generatedFile.readText()
        assertTrue(generatedText.contains("public val MAX_CACHE_BYTES: Long = 9876543210L"))
        assertTrue(generatedText.contains("public val ROLLOUT_RATIO: Double = 0.75"))
        assertTrue(
            generatedText.contains(
                "public val SUPPORT_LABELS: Map<String, String> = mapOf(\"channel\" to \"stable\")",
            ),
        )
        assertTrue(
            generatedText.contains(
                "public val RELEASE_STAGE: sample.ReleaseStage = sample.ReleaseStage.BETA",
            ),
        )
        assertTrue(generatedText.contains("public val SUPPORT_EMAIL: String? = null"))
        assertTrue(jsonSchemaFile.readText().contains("\"x-kayan-nullable\": true"))
        assertTrue(jsonSchemaFile.readText().contains("\"x-kayan-enumType\": \"sample.ReleaseStage\""))
        assertTrue(
            markdownSchemaFile.readText().contains(
                "Allows explicit null values.",
            ),
        )
    }

    @Test
    fun failsWhenPackageNameIsMissing() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    flavor.set("prod")
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        val result = gradleRunner(projectDir, "generateKayanConfig").buildAndFail()
        assertTrue(result.output.contains("packageName"))
    }

    @Test
    fun failsWhenFlavorIsMissing() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        val result = gradleRunner(projectDir, "generateKayanConfig").buildAndFail()
        assertTrue(result.output.contains("flavor"))
    }

    @Test
    fun failsOnUnknownKeys() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "unknown_key": true
                    }
                  }
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        val result = gradleRunner(projectDir, "generateKayanConfig").buildAndFail()
        assertTrue(result.output.contains("Unknown key 'unknown_key'"))
    }

    @Test
    fun failsOnTypeMismatches() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "max_workspace_count": "wrong"
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        val result = gradleRunner(projectDir, "generateKayanConfig").buildAndFail()
        assertTrue(result.output.contains("expected int but found string"))
    }

    @Test
    fun generatesCustomTypedPropertiesUsingBuildTimeAdapter() {
        val adapterClasspathEntry = File(
            ReleaseStageAdapter::class.java.protectionDomain.codeSource.location.toURI()
        ).absolutePath.replace("\\", "\\\\")
        val projectDir = createProject(
            buildScript = buildScript(
                buildscriptBlock = """
                    dependencies {
                        classpath(files("$adapterClasspathEntry"))
                    }
                """.trimIndent(),
                schemaBlock = """
                    custom(
                        jsonKey = "release_stage",
                        propertyName = "RELEASE_STAGE",
                        rawKind = io.kayan.ConfigValueKind.STRING,
                        adapter = "io.kayan.gradle.ReleaseStageAdapter",
                        required = true,
                    )
                """.trimIndent(),
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "release_stage": "beta"
                }
            """.trimIndent(),
            commonSource = """
                package sample

                enum class ReleaseStage {
                    BETA,
                    PROD,
                }

                val releaseStage: ReleaseStage = sample.config.KayanConfig.RELEASE_STAGE
            """.trimIndent(),
        )

        gradleRunner(projectDir, "compileKotlinJvm").build()

        val generatedFile = File(projectDir, "build/generated/kayan/commonMain/kotlin/sample/config/KayanConfig.kt")
        val generatedText = generatedFile.readText()
        assertTrue(
            generatedText.contains(
                "public val RELEASE_STAGE: sample.ReleaseStage = sample.ReleaseStage.BETA"
            )
        )
    }

    @Test
    fun reusesConfigurationCacheWhenCustomAdapterComesFromBuildscriptClasspath() {
        val adapterClasspathEntry = File(
            ReleaseStageAdapter::class.java.protectionDomain.codeSource.location.toURI()
        ).absolutePath.replace("\\", "\\\\")
        val projectDir = createProject(
            buildScript = buildScript(
                buildscriptBlock = """
                    dependencies {
                        classpath(files("$adapterClasspathEntry"))
                    }
                """.trimIndent(),
                schemaBlock = """
                    custom(
                        jsonKey = "release_stage",
                        propertyName = "RELEASE_STAGE",
                        rawKind = io.kayan.ConfigValueKind.STRING,
                        adapter = "io.kayan.gradle.ReleaseStageAdapter",
                        required = true,
                    )
                """.trimIndent(),
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "release_stage": "beta"
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        val firstRun = gradleRunner(projectDir, "generateKayanConfig", "--configuration-cache").build()
        val secondRun = gradleRunner(projectDir, "generateKayanConfig", "--configuration-cache").build()

        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":generateKayanConfig")?.outcome)
        assertTrue(
            secondRun.task(":generateKayanConfig")?.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
        )
        assertTrue(firstRun.output.contains("Configuration cache entry stored."))
        assertTrue(secondRun.output.contains("Configuration cache entry reused."))

        val generatedFile = File(projectDir, "build/generated/kayan/commonMain/kotlin/sample/config/KayanConfig.kt")
        assertTrue(
            generatedFile.readText().contains(
                "public val RELEASE_STAGE: sample.ReleaseStage = sample.ReleaseStage.BETA"
            )
        )
    }

    @Test
    fun exportsSchemaArtifactsWithoutGeneratingKotlinSource() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        val result = gradleRunner(projectDir, "exportKayanSchema").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":exportKayanSchema")?.outcome)

        val jsonSchemaFile = File(projectDir, "build/generated/kayan/schema/kayan.schema.json")
        val markdownSchemaFile = File(projectDir, "build/generated/kayan/schema/SCHEMA.md")

        assertTrue(jsonSchemaFile.exists())
        assertTrue(markdownSchemaFile.exists())
        assertTrue(jsonSchemaFile.readText().contains("\"required\": [\n    \"flavors\"\n  ]"))
        assertTrue(jsonSchemaFile.readText().contains("\"x-kayan-requiredAfterResolution\": true"))
        assertTrue(
            markdownSchemaFile.readText().contains(
                "Generated Kotlin access point: `sample.config.KayanConfig`.",
            ),
        )
        assertTrue(
            markdownSchemaFile.readText().contains(
                "Keys marked `required` must appear either at the top level or inside every flavor",
            ),
        )
    }

    @Test
    fun exportsSchemaToCustomOutputFiles() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    jsonSchemaOutputFile.set(layout.projectDirectory.file("docs/kayan.schema.json"))
                    markdownSchemaOutputFile.set(layout.projectDirectory.file("docs/kayan-schema.md"))
                """.trimIndent()
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        gradleRunner(projectDir, "exportKayanSchema").build()

        val jsonSchemaFile = File(projectDir, "docs/kayan.schema.json")
        val markdownSchemaFile = File(projectDir, "docs/kayan-schema.md")

        assertTrue(jsonSchemaFile.exists())
        assertTrue(markdownSchemaFile.exists())
        assertTrue(jsonSchemaFile.readText().contains("\"title\": \"Kayan config schema\""))
        assertTrue(
            markdownSchemaFile.readText().contains(
                "| `regional_support_links` | `REGIONAL_SUPPORT_LINKS` | " +
                    "`map<string, array<string>>` | No |",
            ),
        )
    }

    @Test
    fun exportsSchemaWithoutKotlinMultiplatformPlugin() {
        val projectDir = createProject(
            buildScript = """
                plugins {
                    id("io.github.mohamadjaara.kayan")
                }

                repositories {
                    google()
                    mavenCentral()
                }

                kayan {
                    schema {
                        string("bundle_id", "BUNDLE_ID", required = true)
                    }

                    packageName.set("sample.config")
                }
            """.trimIndent(),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        val result = gradleRunner(projectDir, "exportKayanSchema").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":exportKayanSchema")?.outcome)
        assertTrue(File(projectDir, "build/generated/kayan/schema/kayan.schema.json").exists())
        assertTrue(File(projectDir, "build/generated/kayan/schema/SCHEMA.md").exists())
    }

    @Test
    fun failsGenerateTaskLazilyWhenKotlinMultiplatformPluginIsMissing() {
        val projectDir = createProject(
            buildScript = """
                plugins {
                    id("io.github.mohamadjaara.kayan")
                }

                repositories {
                    google()
                    mavenCentral()
                }

                kayan {
                    schema {
                        string("bundle_id", "BUNDLE_ID", required = true)
                    }

                    packageName.set("sample.config")
                    flavor.set("prod")
                }
            """.trimIndent(),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        val result = gradleRunner(projectDir, "generateKayanConfig").buildAndFail()

        assertTrue(result.output.contains("requires `org.jetbrains.kotlin.multiplatform`"))
    }

    private fun createProject(
        buildScript: String,
        baseJson: String,
        commonSource: String,
        customJson: String? = null,
    ): File {
        val projectDir = createTempDirectory(prefix = "kayan-plugin-test").toFile()
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
            """.trimIndent()
        )
        File(projectDir, "build.gradle.kts").writeText(buildScript)
        File(projectDir, "default.json").writeText(baseJson)
        customJson?.let { File(projectDir, "custom-overrides.json").writeText(it) }

        val sourceFile = File(projectDir, "src/commonMain/kotlin/sample/UseConfig.kt")
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(commonSource)
        return projectDir
    }

    private fun buildScript(
        kayanBlock: String,
        schemaBlock: String = "",
        buildscriptBlock: String = "",
    ): String = """
        buildscript {
            $buildscriptBlock
        }

        plugins {
            kotlin("multiplatform") version "2.3.20"
            id("io.github.mohamadjaara.kayan")
        }

        repositories {
            google()
            mavenCentral()
        }

        kotlin {
            jvm()
        }

        kayan {
            schema {
                string("bundle_id", "BUNDLE_ID", required = true)
                string("brand_name", "BRAND_NAME")
                boolean("feature_search_enabled", "FEATURE_SEARCH_ENABLED")
                int("max_workspace_count", "MAX_WORKSPACE_COUNT")
                stringList("support_links", "SUPPORT_LINKS")
                stringListMap("regional_support_links", "REGIONAL_SUPPORT_LINKS")
                $schemaBlock
            }

            $kayanBlock
        }
    """.trimIndent()

    private fun gradleRunner(projectDir: File, vararg tasks: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*tasks, "--stacktrace")
}
