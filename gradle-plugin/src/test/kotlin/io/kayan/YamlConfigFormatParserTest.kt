package io.kayan

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertContains
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

    @Test
    fun parseRootEitherRejectsNonFiniteNumbers() {
        when (
            val result = parser.parseRootEither(
                configText = """
                    flavors:
                      prod:
                        bundle_id: com.example.prod
                    rollout_ratio: 1e309
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
    fun parseRootEitherRejectsEmptyScalarAndSequenceRoots() {
        listOf(
            "" to "null",
            "plain text" to "string",
            "- first\n- second" to "array",
        ).forEach { (configText, expectedType) ->
            when (val result = parser.parseRootEither(configText, "default.yaml")) {
                is Either.Left -> {
                    val error = assertIs<ConfigError.InvalidType>(result.value)
                    assertEquals("configuration root", error.subject)
                    assertEquals(expectedType, error.actualType)
                    assertEquals("default.yaml", error.context.sourceName)
                }

                is Either.Right -> fail("Expected a YAML root type error for $expectedType.")
            }
        }
    }

    @Test
    fun parseRootEitherRejectsDuplicateMappingKeys() {
        val error = parseSyntaxError(
            """
                flavors:
                  prod:
                    bundle_id: com.example.first
                    bundle_id: com.example.second
            """.trimIndent(),
        )

        assertContains(error.detail.orEmpty(), "Duplicate YAML mapping key 'bundle_id'")
    }

    @Test
    fun parseRootEitherRejectsMergeKeys() {
        val error = parseSyntaxError(
            """
                flavors:
                  prod:
                    <<:
                      bundle_id: com.example.base
            """.trimIndent(),
        )

        assertContains(error.detail.orEmpty(), "YAML merge keys are not supported")
    }

    @Test
    fun parseRootEitherRejectsNonDecimalAndOutOfRangeIntegers() {
        listOf("0x10", "9223372036854775808").forEach { invalidInteger ->
            val error = parseSyntaxError(
                """
                    flavors:
                      prod:
                        max_workspace_count: $invalidInteger
                """.trimIndent(),
            )

            assertContains(error.detail.orEmpty(), "YAML integer literal '$invalidInteger'")
        }
    }

    @Test
    fun parseRootEitherRejectsUnsupportedScalarTags() {
        val error = parseSyntaxError(
            """
                flavors:
                  prod:
                    release_date: 2026-07-14
            """.trimIndent(),
        )

        assertContains(error.detail.orEmpty(), "Unsupported YAML scalar tag")
    }

    private fun parseSyntaxError(configText: String): ConfigError.InvalidConfigSyntax = when (
        val result = parser.parseRootEither(configText, "default.yaml")
    ) {
        is Either.Left -> assertIs(result.value)
        is Either.Right -> fail("Expected a YAML syntax error.")
    }
}
