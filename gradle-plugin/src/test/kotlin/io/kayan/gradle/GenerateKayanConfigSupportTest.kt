package io.kayan.gradle

import arrow.core.getOrElse
import io.kayan.ConfigDefinition
import io.kayan.ConfigFormat
import io.kayan.ConfigValue
import io.kayan.ConfigValueKind
import io.kayan.KayanValidationMode
import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GenerateKayanConfigSupportTest {
    private val bundleId = ConfigDefinition(
        jsonKey = "bundle_id",
        propertyName = "BUNDLE_ID",
        kind = ConfigValueKind.STRING,
        required = true,
    )

    @Test
    fun requireConfiguredRejectsBlankValues() {
        val error = assertFailsWith<GradleException> {
            requireConfigured("   ", "packageName")
        }

        assertMessageContains(error, "Kayan requires `packageName` to be configured.")
    }

    @Test
    fun requireSchemaRejectsEmptyEntries() {
        val error = assertFailsWith<GradleException> {
            requireSchema(emptyList())
        }

        assertMessageContains(error, "Kayan requires at least one consumer-defined schema entry.")
    }

    @Test
    fun requireSchemaWrapsSchemaDeserializationErrors() {
        val error = assertFailsWith<GradleException> {
            requireSchema(listOf("[]"))
        }

        assertMessageContains(
            error,
            "Failed to build Kayan schema",
            "Invalid serialized Kayan schema entry.",
        )
    }

    @Test
    fun requireSchemaReportsAllAccumulatedSchemaErrors() {
        val duplicateBundleIdEntry = bundleIdEntry().serialize()

        val error = assertFailsWith<GradleException> {
            requireSchema(listOf(duplicateBundleIdEntry, duplicateBundleIdEntry))
        }

        assertMessageContains(
            error,
            "Failed to build Kayan schema:",
            "- Config schema contains duplicate jsonKey values: 'bundle_id'.",
            "- Config schema contains duplicate propertyName values: 'BUNDLE_ID'.",
        )
    }

    @Test
    fun requireExistingFileRejectsMissingFiles() {
        val tempDir = createTempDirectory(prefix = "kayan-support-test").toFile()

        val error = assertFailsWith<GradleException> {
            requireExistingFile(File(tempDir, "missing.json"), "base")
        }

        assertMessageContains(error, "Kayan base config file does not exist:")
    }

    @Test
    fun resolveConfigWrapsValidationErrorsInGradleException() {
        val tempDir = createTempDirectory(prefix = "kayan-support-test").toFile()
        val baseFile = File(tempDir, "default.json").apply {
            writeText(
                """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      },
                      "brand_name_typo": "Example"
                    }
                """.trimIndent()
            )
        }
        val schema = requireSchema(listOf(bundleIdEntry().serialize()))

        val error = assertFailsWith<GradleException> {
            resolveConfig(
                schema = schema,
                baseFile = baseFile,
                customFile = null,
                validationMode = KayanValidationMode.STRICT,
            )
        }

        assertMessageContains(
            error,
            "Failed to resolve Kayan config",
            "Unknown key 'brand_name_typo'",
        )
        assertTrue(error.cause is IllegalArgumentException)
    }

    @Test
    fun resolveConfigDefaultsToSubsetModeForSharedFiles() {
        val brandName = ConfigDefinition(
            jsonKey = "brand_name",
            propertyName = "BRAND_NAME",
            kind = ConfigValueKind.STRING,
        )
        val schema = requireSchema(
            listOf(
                bundleIdEntry().serialize(),
                KayanSchemaEntrySpec(
                    jsonKey = brandName.jsonKey,
                    propertyName = brandName.propertyName,
                    kind = brandName.kind,
                    required = brandName.required,
                    nullable = brandName.nullable,
                ).serialize(),
            ),
        )

        val resolved = resolveConfig(
            schema = schema,
            baseFile = createConfigFile(
                """
                    {
                      "unknown_root_key": true,
                      "targets": {
                        "jvm": {
                          "brand_name": "Base JVM",
                          "unknown_target_key": "ignored"
                        }
                      },
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod",
                          "targets": {
                            "jvm": {
                              "brand_name": "Prod JVM",
                              "unknown_flavor_target_key": 42
                            }
                          }
                        }
                      }
                    }
                """.trimIndent(),
            ),
            customFile = null,
            targetName = "jvm",
        )

        assertEquals(
            ConfigValue.StringValue("Prod JVM"),
            resolved.flavors.getValue("prod").values.getValue(brandName),
        )
    }

    @Test
    fun resolveTargetDeclarationNullabilityTreatsOptionalNonNullableKeysAsNonNullWhenAllTargetsResolveThem() {
        val brandName = ConfigDefinition(
            jsonKey = "brand_name",
            propertyName = "BRAND_NAME",
            kind = ConfigValueKind.STRING,
        )
        val schema = requireSchema(
            listOf(
                bundleIdEntry().serialize(),
                KayanSchemaEntrySpec(
                    jsonKey = brandName.jsonKey,
                    propertyName = brandName.propertyName,
                    kind = brandName.kind,
                    required = brandName.required,
                    nullable = brandName.nullable,
                ).serialize(),
            ),
        )

        val declarationNullability = resolveTargetDeclarationNullabilityEither(
            schema = schema,
            flavorName = "prod",
            targetNames = listOf("jvm", "ios"),
            baseFile = createConfigFile(
                """
                    {
                      "targets": {
                        "jvm": {
                          "brand_name": "Base JVM"
                        },
                        "ios": {
                          "brand_name": "Base iOS"
                        }
                      },
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      }
                    }
                """.trimIndent(),
            ),
            customFile = null,
        ).getOrElse { throw it.toGradleException() }

        assertEquals(false, declarationNullability.getValue(brandName))
    }

    @Test
    fun resolveTargetDeclarationNullabilityFailsWhenNonNullableKeyIsMissingOnOneGeneratedTarget() {
        val brandName = ConfigDefinition(
            jsonKey = "brand_name",
            propertyName = "BRAND_NAME",
            kind = ConfigValueKind.STRING,
        )
        val schema = requireSchema(
            listOf(
                bundleIdEntry().serialize(),
                KayanSchemaEntrySpec(
                    jsonKey = brandName.jsonKey,
                    propertyName = brandName.propertyName,
                    kind = brandName.kind,
                    required = brandName.required,
                    nullable = brandName.nullable,
                ).serialize(),
            ),
        )

        val error = assertFailsWith<GradleException> {
            resolveTargetDeclarationNullabilityEither(
                schema = schema,
                flavorName = "prod",
                targetNames = listOf("jvm", "ios"),
                baseFile = createConfigFile(
                    """
                        {
                          "targets": {
                            "jvm": {
                              "brand_name": "Base JVM"
                            }
                          },
                          "flavors": {
                            "prod": {
                              "bundle_id": "com.example.prod"
                            }
                          }
                        }
                    """.trimIndent(),
                ),
                customFile = null,
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "could not declare key 'brand_name' as non-null",
            "flavor 'prod'",
            "'ios'",
            "mark the schema entry nullable",
        )
    }

    @Test
    fun resolveConfigSupportsYamlFilesThroughAutoDetection() {
        val brandName = ConfigDefinition(
            jsonKey = "brand_name",
            propertyName = "BRAND_NAME",
            kind = ConfigValueKind.STRING,
        )
        val schema = requireSchema(
            listOf(
                bundleIdEntry().serialize(),
                KayanSchemaEntrySpec(
                    jsonKey = brandName.jsonKey,
                    propertyName = brandName.propertyName,
                    kind = brandName.kind,
                    required = brandName.required,
                    nullable = brandName.nullable,
                ).serialize(),
            ),
        )
        val resolved = resolveConfig(
            schema = schema,
            baseFile = createConfigFile(
                """
                    flavors:
                      prod:
                        bundle_id: com.example.prod
                    brand_name: Example
                """.trimIndent(),
                fileName = "default.yaml",
            ),
            customFile = createConfigFile(
                """
                    flavors: {}
                    brand_name: Custom Example
                """.trimIndent(),
                fileName = "custom-overrides.yml",
            ),
        )

        assertEquals(
            ConfigValue.StringValue("Custom Example"),
            resolved.flavors.getValue("prod").values.getValue(brandName),
        )
    }

    @Test
    fun resolveConfigRejectsProtectedCustomOverrides() {
        val schema = requireSchema(
            listOf(
                bundleIdEntry().serialize(),
                KayanSchemaEntrySpec(
                    jsonKey = "api_secret",
                    propertyName = "API_SECRET",
                    kind = ConfigValueKind.STRING,
                    required = false,
                    nullable = false,
                    preventOverride = true,
                ).serialize(),
            ),
        )

        val error = assertFailsWith<GradleException> {
            resolveConfig(
                schema = schema,
                baseFile = createConfigFile(
                    """
                        {
                          "flavors": {
                            "prod": {
                              "bundle_id": "com.example.prod",
                              "api_secret": "base-secret"
                            }
                          }
                        }
                    """.trimIndent(),
                ),
                customFile = createConfigFile(
                    """
                        {
                          "flavors": {},
                          "api_secret": "custom-secret"
                        }
                    """.trimIndent(),
                    fileName = "custom-overrides.json",
                ),
            )
        }

        assertMessageContains(
            error,
            "Failed to resolve Kayan config",
            "Key 'api_secret'",
            "preventOverride",
        )
    }

    @Test
    fun resolveConfigRejectsMixedFormatsWhenAutoDetecting() {
        val schema = requireSchema(listOf(bundleIdEntry().serialize()))

        val error = assertFailsWith<GradleException> {
            resolveConfig(
                schema = schema,
                baseFile = createConfigFile(
                    """
                        flavors:
                          prod:
                            bundle_id: com.example.prod
                    """.trimIndent(),
                    fileName = "default.yaml",
                ),
                customFile = createConfigFile(
                    """
                        {
                          "flavors": {}
                        }
                    """.trimIndent(),
                    fileName = "custom-overrides.json",
                ),
            )
        }

        assertMessageContains(
            error,
            "uses YAML, but custom config source",
            "uses JSON",
            "configFormat is AUTO",
        )
    }

    @Test
    fun resolveConfigSupportsExplicitYamlFormatOverride() {
        val schema = requireSchema(listOf(bundleIdEntry().serialize()))
        val resolved = resolveConfig(
            schema = schema,
            baseFile = createConfigFile(
                """
                    flavors:
                      prod:
                        bundle_id: com.example.prod
                """.trimIndent(),
                fileName = "default.yaml",
            ),
            customFile = null,
            configFormat = ConfigFormat.YAML,
        )

        assertEquals(
            ConfigValue.StringValue("com.example.prod"),
            resolved.flavors.getValue("prod").values.values.single(),
        )
    }

    @Test
    fun resolveConfigRejectsJsonFilesWhenExplicitYamlFormatIsConfigured() {
        val schema = requireSchema(listOf(bundleIdEntry().serialize()))

        val error = assertFailsWith<GradleException> {
            resolveConfig(
                schema = schema,
                baseFile = createConfigFile(
                    """
                        {
                          "flavors": {
                            "prod": {
                              "bundle_id": "com.example.prod"
                            }
                          }
                        }
                    """.trimIndent(),
                    fileName = "default.json",
                ),
                customFile = null,
                configFormat = ConfigFormat.YAML,
            )
        }

        assertMessageContains(
            error,
            "Configured configFormat is YAML",
            "source '",
            "uses JSON",
        )
    }

    @Test
    fun requireResolvedFlavorRejectsUnknownFlavor() {
        val schema = requireSchema(listOf(bundleIdEntry().serialize()))
        val resolved = resolveConfig(
            schema = schema,
            baseFile = createConfigFile(
                """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      }
                    }
                """.trimIndent()
            ),
            customFile = null,
        )

        val error = assertFailsWith<GradleException> {
            requireResolvedFlavor(resolved, "staging")
        }

        assertMessageContains(
            error,
            "Configured Kayan flavor 'staging' was not found in the resolved config.",
        )
    }

    @Test
    fun runAdapterStepWrapsFailuresWithDefinitionContext() {
        val failure = IllegalStateException("boom")

        val error = assertFailsWith<GradleException> {
            runAdapterStep(bundleId, "parse") {
                throw failure
            }
        }

        assertMessageContains(
            error,
            "Failed to parse key 'bundle_id' with custom adapter 'null'",
            "boom",
        )
        assertSame(failure, error.cause)
    }

    @Test
    fun requireRenderedExpressionRejectsBlankExpressions() {
        val definition = bundleId.copy(adapterClassName = "sample.Adapter")

        val error = assertFailsWith<GradleException> {
            requireRenderedExpression(definition, "   ")
        }

        assertMessageContains(
            error,
            "Custom adapter 'sample.Adapter' for key 'bundle_id' returned a blank Kotlin expression.",
        )
    }

    @Test
    fun toRawValueRejectsNullValues() {
        val error = assertFailsWith<IllegalStateException> {
            ConfigValue.NullValue(ConfigValueKind.STRING).toRawValue()
        }

        assertMessageContains(error, "Null config values do not have a raw adapter value.")
    }

    @Test
    fun toRawValueReturnsWrappedEnumValue() {
        assertEquals("BETA", ConfigValue.EnumValue("BETA").toRawValue())
    }

    @Test
    fun reflectiveSingleArgumentMethodRejectsWrongMethodShape() {
        val error = assertFailsWith<GradleException> {
            reflectiveSingleArgumentMethod(
                adapterClass = InvalidMethodShapeAdapter::class.java,
                methodName = "parse",
                className = "sample.InvalidMethodShapeAdapter",
            )
        }

        assertMessageContains(
            error,
            "Custom adapter 'sample.InvalidMethodShapeAdapter' must define a 'parse' method " +
                "that accepts exactly one argument.",
        )
    }

    @Test
    fun invokeAdapterMethodRejectsNullReturns() {
        val method = reflectiveSingleArgumentMethod(
            adapterClass = NullableResultAdapter::class.java,
            methodName = "renderKotlin",
            className = "sample.NullableResultAdapter",
        )
        val adapter = NullableResultAdapter()

        val error = assertFailsWith<GradleException> {
            invokeAdapterMethod(
                method = method,
                instance = adapter,
                argument = "value",
                className = "sample.NullableResultAdapter",
                methodName = "renderKotlin",
            )
        }

        assertMessageContains(
            error,
            "Failed to invoke 'renderKotlin' on custom adapter 'sample.NullableResultAdapter'",
            "returned null from 'renderKotlin'",
        )
    }

    @Test
    fun invokeAdapterMethodWrapsThrownFailures() {
        val method = reflectiveSingleArgumentMethod(
            adapterClass = ThrowingAdapter::class.java,
            methodName = "parse",
            className = "sample.ThrowingAdapter",
        )
        val adapter = ThrowingAdapter()

        val error = assertFailsWith<GradleException> {
            invokeAdapterMethod(
                method = method,
                instance = adapter,
                argument = "value",
                className = "sample.ThrowingAdapter",
                methodName = "parse",
            )
        }

        assertMessageContains(
            error,
            "Failed to invoke 'parse' on custom adapter 'sample.ThrowingAdapter'",
        )
        assertHasCauseMessage(error, "kaboom")
    }

    private fun bundleIdEntry(): KayanSchemaEntrySpec = KayanSchemaEntrySpec(
        jsonKey = bundleId.jsonKey,
        propertyName = bundleId.propertyName,
        kind = bundleId.kind,
        required = bundleId.required,
        nullable = bundleId.nullable,
    )

    private fun createConfigFile(
        contents: String,
        fileName: String = "default.json",
    ): File {
        val tempDir = createTempDirectory(prefix = "kayan-support-test").toFile()
        return File(tempDir, fileName).apply { writeText(contents) }
    }

    private fun assertHasCauseMessage(
        error: Throwable,
        expectedMessage: String,
    ) {
        val messages = generateSequence(error.cause) { it.cause }
            .map(Throwable::message)
            .filterNotNull()
            .toList()

        assertTrue(
            messages.any { expectedMessage in it },
            "Expected cause chain <$messages> to contain <$expectedMessage>.",
        )
    }

    private class InvalidMethodShapeAdapter {
        fun parse(): String = javaClass.simpleName
    }

    private class NullableResultAdapter {
        fun renderKotlin(value: Any): String? = value.toString().takeIf { false }
    }

    private class ThrowingAdapter {
        fun parse(value: Any): String = error("kaboom: $value")
    }
}
