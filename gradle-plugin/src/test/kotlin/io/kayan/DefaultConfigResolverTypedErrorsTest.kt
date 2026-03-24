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
    fun parseEitherReturnsStructuredInvalidConfigSyntaxError() {
        when (
            val result = resolver.parseEither(
                configJson = "{",
                schema = schema,
                sourceName = "broken.json",
            )
        ) {
            is Either.Left -> {
                val error = assertIs<ConfigError.InvalidConfigSyntax>(result.value)
                assertEquals("broken.json", error.sourceName)
                assertEquals("JSON", error.formatName)
            }

            is Either.Right -> fail("Expected a typed config error.")
        }
    }

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
    fun parseEitherUsesConfiguredParserTree() {
        val resolver = DefaultConfigResolver(
            parser = object : ConfigFormatParser {
                override val formatName: String = "TEST"

                override fun parseRootEither(
                    configText: String,
                    sourceName: String,
                ): Either<ConfigError, ConfigNode.ObjectNode> = Either.Right(
                    ConfigNode.ObjectNode(
                        entries = mapOf(
                            "brand_name" to ConfigNode.StringNode("Example"),
                            DefaultConfigResolver.FLAVORS_KEY to ConfigNode.ObjectNode(
                                entries = mapOf(
                                    "prod" to ConfigNode.ObjectNode(
                                        entries = mapOf(
                                            "bundle_id" to ConfigNode.StringNode("com.example.prod"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            },
        )
        val schema = ConfigSchema(
            listOf(
                ConfigDefinition(
                    jsonKey = "bundle_id",
                    propertyName = "BUNDLE_ID",
                    kind = ConfigValueKind.STRING,
                    required = true,
                ),
                ConfigDefinition(
                    jsonKey = "brand_name",
                    propertyName = "BRAND_NAME",
                    kind = ConfigValueKind.STRING,
                ),
            ),
        )

        when (
            val result = resolver.parseEither(
                configJson = "ignored",
                schema = schema,
                sourceName = "ignored.test",
            )
        ) {
            is Either.Left -> fail("Expected parsed config from canonical tree.")
            is Either.Right -> {
                assertEquals(
                    ConfigValue.StringValue("Example"),
                    result.value.defaults.values.entries.single().value,
                )
                assertEquals(
                    ConfigValue.StringValue("com.example.prod"),
                    result.value.flavors.getValue("prod").values.entries.single().value,
                )
            }
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
