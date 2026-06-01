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

    @Test
    fun resolveEitherReturnsStructuredPreventedCustomOverrideError() {
        val protectedSchema = ConfigSchema(
            listOf(
                ConfigDefinition(
                    jsonKey = "bundle_id",
                    propertyName = "BUNDLE_ID",
                    kind = ConfigValueKind.STRING,
                    required = true,
                ),
                ConfigDefinition(
                    jsonKey = "api_secret",
                    propertyName = "API_SECRET",
                    kind = ConfigValueKind.STRING,
                    preventOverride = true,
                ),
            ),
        )

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
                schema = protectedSchema,
                customConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "api_secret": "secret"
                        }
                      }
                    }
                """.trimIndent(),
                defaultConfigSourceName = "base.json",
                customConfigSourceName = "custom.json",
            )
        ) {
            is Either.Left -> {
                val error = assertIs<ConfigError.PreventedCustomOverride>(result.value)
                assertEquals("api_secret", error.definition.jsonKey)
                assertEquals("base.json", error.defaultConfigSourceName)
                assertEquals("$.flavors.prod.api_secret", error.customContext.path)
                assertEquals("custom.json", error.customContext.sourceName)
            }

            is Either.Right -> fail("Expected a typed config error.")
        }
    }

    @Test
    fun parseEitherReturnsStructuredInvalidTargetSectionError() {
        when (
            val result = resolver.parseEither(
                configJson = """
                    {
                      "targets": {
                        "ios": []
                      },
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      }
                    }
                """.trimIndent(),
                schema = schema,
                sourceName = "default.json",
            )
        ) {
            is Either.Left -> {
                val error = assertIs<ConfigError.InvalidType>(result.value)
                assertEquals("value for target 'ios'", error.subject)
                assertEquals("object", error.expectedType)
                assertEquals("array", error.actualType)
                assertEquals("$.targets.ios", error.context.path)
            }

            is Either.Right -> fail("Expected a typed config error.")
        }
    }

    @Test
    fun parseEitherReturnsStructuredStringListMapEntryError() {
        val supportLinks = ConfigDefinition(
            jsonKey = "regional_support_links",
            propertyName = "REGIONAL_SUPPORT_LINKS",
            kind = ConfigValueKind.STRING_LIST_MAP,
        )
        val schema = ConfigSchema(listOf(this.schema.entries.single(), supportLinks))

        when (
            val result = resolver.parseEither(
                configJson = """
                    {
                      "regional_support_links": {
                        "eu": [true]
                      },
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      }
                    }
                """.trimIndent(),
                schema = schema,
                sourceName = "default.json",
            )
        ) {
            is Either.Left -> {
                val error = assertIs<ConfigError.InvalidType>(result.value)
                assertEquals("list entry for key 'regional_support_links'", error.subject)
                assertEquals("string", error.expectedType)
                assertEquals("boolean", error.actualType)
                assertEquals("$.regional_support_links.eu[0]", error.context.path)
            }

            is Either.Right -> fail("Expected a typed config error.")
        }
    }

    @Test
    fun parseEitherReturnsStructuredInvalidEnumNormalizationError() {
        val releaseStage = ConfigDefinition(
            jsonKey = "release_stage",
            propertyName = "RELEASE_STAGE",
            kind = ConfigValueKind.ENUM,
            enumTypeName = "sample.ReleaseStage",
        )
        val schema = ConfigSchema(listOf(this.schema.entries.single(), releaseStage))

        when (
            val result = resolver.parseEither(
                configJson = """
                    {
                      "release_stage": "---",
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      }
                    }
                """.trimIndent(),
                schema = schema,
                sourceName = "default.json",
            )
        ) {
            is Either.Left -> {
                val error = assertIs<ConfigError.InvalidEnumValue>(result.value)
                assertEquals("release_stage", error.jsonKey)
                assertEquals("$.release_stage", error.context.path)
                assertEquals("Enum values must contain at least one letter or digit.", error.detail)
            }

            is Either.Right -> fail("Expected a typed config error.")
        }
    }
}
