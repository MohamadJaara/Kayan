package io.kayan.gradle

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

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

        val actual = resolveWithValueSource(baseFile = baseFile)

        assertEquals(
            mapOf(
                "brand_name" to ResolvedBuildValue("brand_name", io.kayan.ConfigValueKind.STRING, "Example"),
                "bundle_id" to ResolvedBuildValue(
                    "bundle_id",
                    io.kayan.ConfigValueKind.STRING,
                    "com.example.prod",
                ),
            ),
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

        val actual = resolveWithValueSource(baseFile = baseFile, customFile = customFile)

        assertEquals("Custom Example", actual.getValue("brand_name").rawValue)
    }

    private fun resolveWithValueSource(
        baseFile: File,
        customFile: File? = null,
    ): Map<String, ResolvedBuildValue> {
        val project = ProjectBuilder.builder().build()
        val provider = project.providers.of(KayanConfigValueSource::class.java) { spec ->
            spec.parameters.baseConfigFile.set(baseFile)
            customFile?.let { spec.parameters.customConfigFile.set(it) }
            spec.parameters.flavor.set("prod")
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
                ),
            )
        }

        return deserializeResolvedValues(provider.get())
    }
}
