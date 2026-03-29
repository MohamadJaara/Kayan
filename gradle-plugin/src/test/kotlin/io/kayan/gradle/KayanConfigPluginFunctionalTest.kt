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
    fun subprojectInheritsRootConfigAndCanOverrideInheritedFlavor() {
        val projectDir = createMultiProject(
            rootBuildScript = """
                plugins {
                    kotlin("jvm") version "2.3.20" apply false
                    id("io.github.mohamadjaara.kayan")
                }

                repositories {
                    google()
                    mavenCentral()
                }

                kayanRoot {
                    flavor.set("prod")
                    baseConfigFile.set(layout.projectDirectory.file("shared.json"))
                    schema {
                        boolean("feature_search_enabled", "FEATURE_SEARCH_ENABLED", required = true)
                    }
                }
            """.trimIndent(),
            childBuildScript = """
                plugins {
                    kotlin("jvm")
                    id("io.github.mohamadjaara.kayan")
                }

                repositories {
                    google()
                    mavenCentral()
                }

                kayan {
                    inheritFromRoot()
                    packageName.set("sample.feature")
                    className.set("FeatureConfig")
                    flavor.set("staging")
                    schema {
                        include("feature_search_enabled")
                    }
                }
            """.trimIndent(),
            sharedConfigJson = """
                {
                  "flavors": {
                    "prod": {
                      "feature_search_enabled": false
                    },
                    "staging": {
                      "feature_search_enabled": true
                    }
                  }
                }
            """.trimIndent(),
            childSource = """
                package sample

                import sample.feature.FeatureConfig

                val featureSearchEnabled: Boolean = FeatureConfig.FEATURE_SEARCH_ENABLED
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, ":feature:compileKotlin").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":feature:generateKayanConfig")?.outcome)
        val generatedFile = File(projectDir, "feature/build/generated/kayan/kotlin/sample/feature/FeatureConfig.kt")
        assertTrue(generatedFile.exists())
        assertTrue(generatedFile.readText().contains("public const val FEATURE_SEARCH_ENABLED: Boolean = true"))
    }

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
        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
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

        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
        val generatedText = generatedFile.readText()
        assertContainsNormalized(
            generatedText,
            "public val SUPPORT_LINKS: List<String> = listOf(\"https://custom.example.com/help\")",
        )
        assertContainsNormalized(
            generatedText,
            "public val REGIONAL_SUPPORT_LINKS: Map<String, List<String>> = " +
                "mapOf(\"example.com\" to listOf(\"sha-a\"))",
        )
        assertTrue(generatedText.contains("public val BRAND_NAME: String = \"Custom Flavor\""))
    }

    @Test
    fun generatesConfigFromYamlFiles() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                    baseConfigFile.set(layout.projectDirectory.file("default.yaml"))
                    customConfigFile.set(layout.projectDirectory.file("custom-overrides.yaml"))
                    configFormat.set(io.kayan.ConfigFormat.YAML)
                """.trimIndent(),
            ),
            baseJson = """
                flavors:
                  prod:
                    bundle_id: com.example.prod
                brand_name: Base
                support_links:
                  - https://example.com/help
            """.trimIndent(),
            customJson = """
                flavors:
                  prod:
                    brand_name: Custom Flavor
                support_links:
                  - https://custom.example.com/help
            """.trimIndent(),
            commonSource = """
                package sample

                import sample.config.KayanConfig

                val bundleId: String = KayanConfig.BUNDLE_ID
                val brandName: String? = KayanConfig.BRAND_NAME
                val links = KayanConfig.SUPPORT_LINKS
            """.trimIndent(),
            baseFileName = "default.yaml",
            customFileName = "custom-overrides.yaml",
        )

        val result = gradleRunner(projectDir, "compileKotlinJvm").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanConfig")?.outcome)
        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
        val generatedText = generatedFile.readText()
        assertTrue(generatedText.contains("public const val BUNDLE_ID: String = \"com.example.prod\""))
        assertTrue(generatedText.contains("public val BRAND_NAME: String = \"Custom Flavor\""))
        assertTrue(
            generatedText.contains(
                "public val SUPPORT_LINKS: List<String> = listOf(\"https://custom.example.com/help\")",
            ),
        )
    }

    @Test
    fun generatesExpectInCommonMainAndActualForConfiguredConvenienceTarget() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                    targets("jvm")
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "targets": {
                        "jvm": {
                          "bundle_id": "com.example.jvm"
                        }
                      }
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

        val result = gradleRunner(projectDir, "compileKotlinJvm").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanJvmMainConfig")?.outcome)
        val expectFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
        val actualFile = File(projectDir, "build/generated/kayan-targets/kotlin/jvmMain/sample/config/KayanConfig.kt")
        assertTrue(expectFile.exists())
        assertTrue(actualFile.exists())
        assertTrue(expectFile.readText().contains("public expect object KayanConfig"))
        assertTrue(actualFile.readText().contains("public actual object KayanConfig"))
        assertTrue(actualFile.readText().contains("public actual val BUNDLE_ID: String = \"com.example.jvm\""))
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
        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
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

        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
        val jsonSchemaFile = File(projectDir, "build/generated/kayan/schema/kayan.schema.json")
        val markdownSchemaFile = File(projectDir, "build/generated/kayan/schema/SCHEMA.md")
        val generatedText = generatedFile.readText()
        assertContainsNormalized(generatedText, "public val MAX_CACHE_BYTES: Long = 9_876_543_210L")
        assertTrue(generatedText.contains("public val ROLLOUT_RATIO: Double = 0.75"))
        assertContainsNormalized(
            generatedText,
            "public val SUPPORT_LABELS: Map<String, String> = mapOf(\"channel\" to \"stable\")",
        )
        assertContainsNormalized(
            generatedText,
            "public val RELEASE_STAGE: ReleaseStage = sample.ReleaseStage.BETA",
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
    fun failsWhenCustomConfigOverridesProtectedKey() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                    customConfigFile.set(layout.projectDirectory.file("custom-overrides.json"))
                """.trimIndent(),
                schemaBlock = """
                    string("api_secret", "API_SECRET", preventOverride = true)
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "api_secret": "base-secret"
                    }
                  }
                }
            """.trimIndent(),
            customJson = """
                {
                  "flavors": {},
                  "api_secret": "custom-secret"
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        val result = gradleRunner(projectDir, "generateKayanConfig").buildAndFail()

        assertTrue(result.output.contains("Key 'api_secret'"))
        assertTrue(result.output.contains("preventOverride"))
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
                    validationMode.set(io.kayan.KayanValidationMode.STRICT)
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
    fun defaultsToSubsetValidationForSharedConfigKeysIncludingTargets() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                    flavor.set("prod")
                    targets("jvm")
                """.trimIndent()
            ),
            baseJson = """
                {
                  "unknown_root_key": true,
                  "targets": {
                    "jvm": {
                      "brand_name": "Base JVM",
                      "unknown_default_target_key": "ignored"
                    }
                  },
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "targets": {
                        "jvm": {
                          "brand_name": "Prod JVM",
                          "unknown_flavor_target_key": 1
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            commonSource = """
                package sample

                import sample.config.KayanConfig

                val bundleId: String = KayanConfig.BUNDLE_ID
                val brandName: String? = KayanConfig.BRAND_NAME
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "compileKotlinJvm").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanJvmMainConfig")?.outcome)
        val actualFile = File(projectDir, "build/generated/kayan-targets/kotlin/jvmMain/sample/config/KayanConfig.kt")
        assertTrue(actualFile.exists())
        assertTrue(actualFile.readText().contains("public actual val BRAND_NAME: String = \"Prod JVM\""))
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

        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
        val generatedText = generatedFile.readText()
        assertContainsNormalized(
            generatedText,
            "public val RELEASE_STAGE: ReleaseStage = sample.ReleaseStage.BETA",
        )
    }

    @Test
    fun generatesNullableCustomTypedPropertiesWhenAdapterValueIsMissing() {
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
                        nullable = true,
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
                  }
                }
            """.trimIndent(),
            commonSource = """
                package sample

                enum class ReleaseStage {
                    BETA,
                    PROD,
                }

                val releaseStage: ReleaseStage? = sample.config.KayanConfig.RELEASE_STAGE
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "compileKotlinJvm").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinJvm")?.outcome)

        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
        assertTrue(generatedFile.exists())
        assertContainsNormalized(
            generatedFile.readText(),
            "public val RELEASE_STAGE: ReleaseStage? = null",
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

        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
        assertContainsNormalized(
            generatedFile.readText(),
            "public val RELEASE_STAGE: ReleaseStage = sample.ReleaseStage.BETA",
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
                "Keys marked `required` must resolve for the selected flavor and optional target",
            ),
        )
    }

    @Test
    fun exportsPreventOverrideMetadataInSchemaArtifacts() {
        val projectDir = createProject(
            buildScript = buildScript(
                kayanBlock = """
                    packageName.set("sample.config")
                """.trimIndent(),
                schemaBlock = """
                    string("api_secret", "API_SECRET", preventOverride = true)
                """.trimIndent(),
            ),
            baseJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "api_secret": "base-secret"
                }
            """.trimIndent(),
            commonSource = "package sample",
        )

        val result = gradleRunner(projectDir, "exportKayanSchema").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":exportKayanSchema")?.outcome)

        val jsonSchemaFile = File(projectDir, "build/generated/kayan/schema/kayan.schema.json")
        val markdownSchemaFile = File(projectDir, "build/generated/kayan/schema/SCHEMA.md")

        assertTrue(jsonSchemaFile.readText().contains("\"x-kayan-preventOverride\": true"))
        assertTrue(
            jsonSchemaFile.readText().contains(
                "\"description\": \"Cannot be set in custom config files; only the main config file may define it.\"",
            ),
        )
        assertTrue(
            markdownSchemaFile.readText().contains(
                "Keys marked `preventOverride` can only be defined in the main config file.",
            ),
        )
        assertTrue(
            markdownSchemaFile.readText().contains(
                "| `api_secret` | `API_SECRET` | `string` | No | " +
                    "Cannot be set in custom config files; only the main config file may define it. |",
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
    fun generatesConfigForJvmOnlyProject() {
        val projectDir = createProject(
            buildScript = jvmBuildScript(
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
            sourceDir = "src/main/kotlin",
        )

        val result = gradleRunner(projectDir, "compileKotlin").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":exportKayanSchema")?.outcome)
        val generatedFile = File(projectDir, "build/generated/kayan/kotlin/sample/config/KayanConfig.kt")
        assertTrue(generatedFile.exists())
        assertTrue(generatedFile.readText().contains("public object KayanConfig"))
        assertTrue(generatedFile.readText().contains("public const val BUNDLE_ID: String = \"com.example.prod\""))
    }

    @Test
    fun failsGenerateTaskLazilyWhenNoKotlinPluginIsApplied() {
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

        assertTrue(result.output.contains("The `io.github.mohamadjaara.kayan` plugin requires one of"))
    }

    private fun createProject(
        buildScript: String,
        baseJson: String,
        commonSource: String,
        customJson: String? = null,
        sourceDir: String = "src/commonMain/kotlin",
        baseFileName: String = "default.json",
        customFileName: String = "custom-overrides.json",
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
        File(projectDir, baseFileName).writeText(baseJson)
        customJson?.let { File(projectDir, customFileName).writeText(it) }

        val sourceFile = File(projectDir, "$sourceDir/sample/UseConfig.kt")
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

    private fun jvmBuildScript(
        kayanBlock: String,
        schemaBlock: String = "",
    ): String = """
        plugins {
            kotlin("jvm") version "2.3.20"
            id("io.github.mohamadjaara.kayan")
        }

        repositories {
            google()
            mavenCentral()
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

    private fun createMultiProject(
        rootBuildScript: String,
        childBuildScript: String,
        sharedConfigJson: String,
        childSource: String,
        childProjectName: String = "feature",
    ): File {
        val projectDir = createTempDirectory(prefix = "kayan-plugin-multiproject-test").toFile()
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

                rootProject.name = "sample-root"
                include(":$childProjectName")
            """.trimIndent()
        )
        File(projectDir, "build.gradle.kts").writeText(rootBuildScript)
        File(projectDir, "shared.json").writeText(sharedConfigJson)

        val childProjectDir = File(projectDir, childProjectName).apply {
            mkdirs()
        }
        File(childProjectDir, "build.gradle.kts").writeText(childBuildScript)

        val sourceFile = File(childProjectDir, "src/main/kotlin/sample/UseConfig.kt")
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(childSource)
        return projectDir
    }

    private fun assertContainsNormalized(
        actual: String,
        expected: String,
    ) {
        assertTrue(
            normalizeWhitespace(actual).contains(normalizeWhitespace(expected)),
            "Expected normalized source to contain <$expected>.\nActual:\n$actual",
        )
    }

    private fun normalizeWhitespace(value: String): String = value.replace(Regex("\\s+"), " ").trim()
}
