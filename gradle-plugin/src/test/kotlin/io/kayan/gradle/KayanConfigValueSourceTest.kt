package io.kayan.gradle

import io.kayan.ConfigFormat
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalKayanGradleApi::class)
class KayanConfigValueSourceTest {
    @Test
    fun resolvesBaseConfigValuesThroughValueSource() {
        val tempDir = createTempDirectory(prefix = "kayan-value-source-test").toFile()
        val baseFile = File(tempDir, "default.json").apply {
            writeText(
                """
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
        }

        val actual = resolveWithValueSource(baseFile = baseFile, jsonKey = "brand_name")

        assertEquals(
            ResolvedBuildValue("brand_name", io.kayan.ConfigValueKind.STRING, "Example"),
            actual,
        )
    }

    @Test
    fun appliesCustomConfigOverridesThroughValueSource() {
        val tempDir = createTempDirectory(prefix = "kayan-value-source-test").toFile()
        val baseFile = File(tempDir, "default.json").apply {
            writeText(
                """
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
        }
        val customFile = File(tempDir, "custom.json").apply {
            writeText(
                """
                    {
                      "flavors": {},
                      "brand_name": "Custom Example"
                    }
                """.trimIndent(),
            )
        }

        val actual = resolveWithValueSource(
            baseFile = baseFile,
            customFile = customFile,
            jsonKey = "brand_name",
        )

        assertEquals("Custom Example", actual.rawValue)
    }

    @Test
    fun resolvesJvmTargetSpecificBrandNameThroughValueSource() {
        val tempDir = createTempDirectory(prefix = "kayan-value-source-test").toFile()
        val baseFile = File(tempDir, "default.json").apply {
            writeText(
                """
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
        }

        val actual = resolveWithValueSource(
            baseFile = baseFile,
            jsonKey = "brand_name",
            targetName = "jvm",
        )

        assertEquals(
            ResolvedBuildValue("brand_name", io.kayan.ConfigValueKind.STRING, "Example JVM"),
            actual,
        )
    }

    @Test
    fun resolvesYamlConfigValuesThroughValueSource() {
        val tempDir = createTempDirectory(prefix = "kayan-value-source-test").toFile()
        val baseFile = File(tempDir, "default.yaml").apply {
            writeText(
                """
                    flavors:
                      prod:
                        bundle_id: com.example.prod
                    brand_name: Example
                """.trimIndent(),
            )
        }

        val actual = resolveWithValueSource(baseFile = baseFile, jsonKey = "bundle_id")

        assertEquals("com.example.prod", actual.rawValue)
    }

    @Test
    fun serializesOnlyRequestedValueThroughValueSource() {
        val tempDir = createTempDirectory(prefix = "kayan-value-source-test").toFile()
        val baseFile = File(tempDir, "default.json").apply {
            writeText(
                """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod",
                          "api_secret": "secret-prod-token"
                        }
                      },
                      "brand_name": "Example"
                    }
                """.trimIndent(),
            )
        }

        val serialized = resolveSerializedWithValueSource(baseFile = baseFile, jsonKey = "brand_name")

        assertFalse(serialized.contains("api_secret"))
        assertFalse(serialized.contains("secret-prod-token"))
        assertFalse(serialized.contains("brand_name"))
    }

    private fun resolveWithValueSource(
        baseFile: File,
        jsonKey: String,
        customFile: File? = null,
        targetName: String? = null,
    ): ResolvedBuildValue =
        deserializeResolvedBuildValue(
            jsonKey = jsonKey,
            serialized = resolveSerializedWithValueSource(
                baseFile = baseFile,
                customFile = customFile,
                jsonKey = jsonKey,
                targetName = targetName,
            ),
        )

    private fun resolveSerializedWithValueSource(
        baseFile: File,
        jsonKey: String,
        customFile: File? = null,
        targetName: String? = null,
    ): String {
        val project = ProjectBuilder.builder().build()
        val provider = project.providers.of(KayanConfigValueSource::class.java) { spec ->
            spec.parameters.baseConfigFile.set(baseFile)
            customFile?.let { spec.parameters.customConfigFile.set(it) }
            spec.parameters.configFormat.set(ConfigFormat.AUTO)
            spec.parameters.flavor.set("prod")
            spec.parameters.jsonKey.set(jsonKey)
            targetName?.let(spec.parameters.targetName::set)
            spec.parameters.schemaEntries.set(
                listOf(
                    KayanSchemaEntrySpec(
                        jsonKey = "bundle_id",
                        propertyName = "BUNDLE_ID",
                        kind = io.kayan.ConfigValueKind.STRING,
                        required = true,
                        nullable = false,
                    ).serialize(),
                    KayanSchemaEntrySpec(
                        jsonKey = "brand_name",
                        propertyName = "BRAND_NAME",
                        kind = io.kayan.ConfigValueKind.STRING,
                        required = false,
                        nullable = false,
                    ).serialize(),
                    KayanSchemaEntrySpec(
                        jsonKey = "api_secret",
                        propertyName = "API_SECRET",
                        kind = io.kayan.ConfigValueKind.STRING,
                        required = false,
                        nullable = false,
                    ).serialize(),
                ),
            )
        }

        return provider.get()
    }
}
