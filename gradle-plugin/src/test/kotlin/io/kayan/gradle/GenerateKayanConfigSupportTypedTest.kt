package io.kayan.gradle

import arrow.core.Either
import io.kayan.ConfigDefinition
import io.kayan.ConfigError
import io.kayan.ConfigValueKind
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class GenerateKayanConfigSupportTypedTest {
    private val bundleId = ConfigDefinition(
        jsonKey = "bundle_id",
        propertyName = "BUNDLE_ID",
        kind = ConfigValueKind.STRING,
        required = true,
    )

    @Test
    fun requireConfiguredEitherReturnsTypedConfigurationError() {
        when (val result = requireConfiguredEither("   ", "packageName")) {
            is Either.Left -> {
                val error = assertIs<PluginConfigurationError.MissingRequiredProperty>(result.value)
                assertEquals("packageName", error.propertyName)
            }

            is Either.Right -> fail("Expected a configuration error.")
        }
    }

    @Test
    fun resolveConfigEitherWrapsTypedConfigErrors() {
        val tempDir = createTempDirectory(prefix = "kayan-support-typed").toFile()
        val baseFile = File(tempDir, "default.json").apply {
            writeText(
                """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      },
                      "unknown_key": true
                    }
                """.trimIndent()
            )
        }
        val schema = requireSchema(listOf(bundleIdEntry().serialize()))

        when (val result = resolveConfigEither(schema, baseFile, null)) {
            is Either.Left -> {
                val error = assertIs<GenerationError.ConfigResolutionFailure>(result.value)
                assertIs<ConfigError.UnknownKey>(error.error)
            }

            is Either.Right -> fail("Expected a typed generation error.")
        }
    }

    @Test
    fun invokeAdapterMethodEitherReturnsTypedInvocationError() {
        val method = ThrowingAdapter::class.java.getMethod("parse", Any::class.java)
        val adapter = ThrowingAdapter()

        when (
            val result = invokeAdapterMethodEither(
                method = method,
                instance = adapter,
                argument = "value",
                className = "sample.ThrowingAdapter",
                methodName = "parse",
            )
        ) {
            is Either.Left -> {
                val error = assertIs<GenerationError.AdapterMethodInvocationFailure>(result.value)
                assertEquals("sample.ThrowingAdapter", error.className)
                assertEquals("parse", error.methodName)
            }

            is Either.Right -> fail("Expected a typed adapter invocation error.")
        }
    }

    private fun bundleIdEntry(): KayanSchemaEntrySpec = KayanSchemaEntrySpec(
        jsonKey = bundleId.jsonKey,
        propertyName = bundleId.propertyName,
        kind = bundleId.kind,
        required = bundleId.required,
        nullable = bundleId.nullable,
    )

    private class ThrowingAdapter {
        fun parse(value: Any): String = error("kaboom: $value")
    }
}
