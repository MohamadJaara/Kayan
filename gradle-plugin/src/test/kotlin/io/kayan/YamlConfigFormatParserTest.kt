package io.kayan

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class YamlConfigFormatParserTest {
    private val parser = YamlConfigFormatParser()

    @Test
    fun parseRootEitherParsesSupportedYamlSubset() {
        when (
            val result = parser.parseRootEither(
                configText = """
                    flavors:
                      prod:
                        bundle_id: com.example.prod
                    feature_search_enabled: true
                    max_workspace_count: 3
                    max_cache_bytes: 9876543210
                    rollout_ratio: 0.75
                    support_links:
                      - https://example.com/help
                    support_labels:
                      channel: stable
                    support_email: null
                """.trimIndent(),
                sourceName = "default.yaml",
            )
        ) {
            is Either.Left -> fail("Expected YAML to parse successfully.")
            is Either.Right -> {
                assertEquals(
                    ConfigNode.BooleanNode(true),
                    result.value.entries.getValue("feature_search_enabled"),
                )
                assertEquals(
                    ConfigNode.IntNode(3),
                    result.value.entries.getValue("max_workspace_count"),
                )
                assertEquals(
                    ConfigNode.LongNode(9876543210),
                    result.value.entries.getValue("max_cache_bytes"),
                )
                assertEquals(
                    ConfigNode.DoubleNode(0.75),
                    result.value.entries.getValue("rollout_ratio"),
                )
                assertEquals(
                    ConfigNode.NullNode,
                    result.value.entries.getValue("support_email"),
                )
            }
        }
    }

    @Test
    fun parseRootEitherRejectsMultipleDocuments() {
        when (
            val result = parser.parseRootEither(
                configText = """
                    flavors:
                      prod:
                        bundle_id: com.example.prod
                    ---
                    flavors:
                      dev:
                        bundle_id: com.example.dev
                """.trimIndent(),
                sourceName = "default.yaml",
            )
        ) {
            is Either.Left -> {
                val error = assertIs<ConfigError.InvalidConfigSyntax>(result.value)
                assertEquals("YAML", error.formatName)
            }

            is Either.Right -> fail("Expected a YAML syntax error.")
        }
    }

    @Test
    fun parseRootEitherRejectsNonStringMappingKeys() {
        when (
            val result = parser.parseRootEither(
                configText = """
                    flavors:
                      prod:
                        bundle_id: com.example.prod
                    true: invalid
                """.trimIndent(),
                sourceName = "default.yaml",
            )
        ) {
            is Either.Left -> {
                val error = assertIs<ConfigError.InvalidConfigSyntax>(result.value)
                assertEquals("YAML", error.formatName)
            }

            is Either.Right -> fail("Expected a YAML syntax error.")
        }
    }
}
