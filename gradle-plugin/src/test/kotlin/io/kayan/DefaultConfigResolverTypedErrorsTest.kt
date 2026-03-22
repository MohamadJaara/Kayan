package io.kayan

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class DefaultConfigResolverTypedErrorsTest {
    private val resolver = DefaultConfigResolver()
    private val schema = ConfigSchema(
        listOf(
            ConfigDefinition(
                jsonKey = "bundle_id",
                propertyName = "BUNDLE_ID",
                kind = ConfigValueKind.STRING,
                required = true,
            ),
        ),
    )

    @Test
    fun parseEitherReturnsStructuredInvalidTypeError() {
        when (
            val result = resolver.parseEither(
                configJson = "[]",
                schema = schema,
                sourceName = "broken.json",
            )
        ) {
            is Either.Left -> {
                val error = assertIs<ConfigError.InvalidType>(result.value)
                assertEquals("configuration root", error.subject)
                assertEquals("object", error.expectedType)
                assertEquals("array", error.actualType)
                assertEquals("broken.json", error.context.sourceName)
                assertEquals("$", error.context.path)
            }

            is Either.Right -> fail("Expected a typed config error.")
        }
    }

    @Test
    fun resolveEitherReturnsStructuredUnknownCustomFlavorError() {
        when (
            val result = resolver.resolveEither(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      }
                    }
                """.trimIndent(),
                schema = schema,
                customConfigJson = """
                    {
                      "flavors": {
                        "dev": {
                          "bundle_id": "com.example.dev"
                        }
                      }
                    }
                """.trimIndent(),
                defaultConfigSourceName = "base.json",
                customConfigSourceName = "custom.json",
            )
        ) {
            is Either.Left -> {
                val error = assertIs<ConfigError.UnknownFlavorInCustomConfig>(result.value)
                assertEquals("dev", error.customFlavor)
                assertEquals("base.json", error.defaultConfigSourceName)
                assertEquals("$.flavors.dev", error.customContext.path)
                assertEquals("custom.json", error.customContext.sourceName)
            }

            is Either.Right -> fail("Expected a typed config error.")
        }
    }
}
