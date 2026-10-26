package io.kayan

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class JsonDuplicateKeyTest {
    private val parser = JsonConfigFormatParser()
    private val resolver = DefaultConfigResolver()
    private val value = ConfigDefinition(
        jsonKey = "value",
        propertyName = "VALUE",
        kind = ConfigValueKind.STRING,
        required = true,
    )
    private val schema = ConfigSchema(listOf(value))

    @Test
    fun rejectsDuplicateKeysThroughoutTheDocument() {
        val cases = listOf(
            Triple("""{"flavors":{"prod":{}},"value":"first","value":"second"}""", "value", "$.value"),
            Triple("""{"flavors":{},"flavors":{}}""", "flavors", "$.flavors"),
            Triple("""{"flavors":{"prod":{},"prod":{}}}""", "prod", "$.flavors.prod"),
            Triple(
                """{"flavors":{"prod":{"value":"first","value":"second"}}}""",
                "value",
                "$.flavors.prod.value",
            ),
            Triple("""{"targets":{"jvm":{},"jvm":{}},"flavors":{}}""", "jvm", "$.targets.jvm"),
            Triple(
                """{"targets":{"jvm":{"value":"first","value":"second"}},"flavors":{}}""",
                "value",
                "$.targets.jvm.value",
            ),
            Triple(
                """{"flavors":{"prod":{"targets":{"jvm":{},"jvm":{}}}}}""",
                "jvm",
                "$.flavors.prod.targets.jvm",
            ),
            Triple(
                """{"flavors":{"prod":{"targets":{"jvm":{"labels":{"en":"first","en":"second"}}}}}}""",
                "en",
                "$.flavors.prod.targets.jvm.labels.en",
            ),
            Triple("""{"labels":{"en":"first","en":"second"}}""", "en", "$.labels.en"),
            Triple("""{"links":{"en":["first"],"en":["second"]}}""", "en", "$.links.en"),
            Triple(
                """{"items":[null,[{"value":"first","value":"second"}]]}""",
                "value",
                "$.items[1][0].value",
            ),
            Triple("""{"":{"":"first","":"second"}}""", "", """$[""][""]"""),
        )

        cases.forEach { (text, key, path) ->
            val error = duplicateError(text)
            assertEquals("base.json", error.sourceName)
            assertEquals("JSON", error.formatName)
            assertEquals("Duplicate JSON object key '$key' at path '$path'.", error.detail)
        }
    }

    @Test
    fun comparesDecodedKeysIncludingEscapesAndSurrogatePairs() {
        val cases = listOf(
            Triple("""{"value":1,"\u0076alue":2}""", "value", "$.value"),
            Triple("""{"\u0076alue":1,"value":2}""", "value", "$.value"),
            Triple("""{"\u0076alue":1,"v\u0061lue":2}""", "value", "$.value"),
            Triple("""{"labels":{"a/b":1,"a\/b":2}}""", "a/b", """$.labels["a/b"]"""),
            Triple("""{"labels":{"a\"b":1,"a\u0022b":2}}""", "a\"b", """$.labels["a\"b"]"""),
            Triple("""{"labels":{"a\\b":1,"a\u005cb":2}}""", "a\\b", """$.labels["a\\b"]"""),
            Triple("""{"labels":{"😃":1,"\uD83D\uDE03":2}}""", "😃", """$.labels["😃"]"""),
            Triple("""{"\u006cabels":{"a.b":1,"a\u002eb":2}}""", "a.b", """$.labels["a.b"]"""),
        )

        cases.forEach { (text, key, path) ->
            assertEquals("Duplicate JSON object key '$key' at path '$path'.", duplicateError(text).detail)
        }
    }

    @Test
    fun rejectsDuplicateDefaultsInStrictAndSubsetResolution() {
        KayanValidationMode.entries.forEach { mode ->
            val result = resolver.resolveEither(
                defaultConfigJson = """{"flavors":{"prod":{}},"value":"first","value":"second"}""",
                schema = schema,
                customConfigJson = null,
                defaultConfigSourceName = "base.json",
                validationMode = mode,
            )

            val error = assertIs<ConfigError.InvalidConfigSyntax>(assertIs<Either.Left<ConfigError>>(result).value)
            assertEquals("base.json", error.sourceName)
            assertTrue(error.detail.orEmpty().contains("$.value"))
        }
    }

    @Test
    fun rejectsDuplicatesInUndeclaredSubsetValues() {
        val result = resolver.parseEither(
            configJson = """{"flavors":{"prod":{}},"value":"ok","ignored":{"en":1,"en":2}}""",
            schema = schema,
            sourceName = "base.json",
            validationMode = KayanValidationMode.SUBSET,
        )

        val error = assertIs<ConfigError.InvalidConfigSyntax>(assertIs<Either.Left<ConfigError>>(result).value)
        assertTrue(error.detail.orEmpty().contains("$.ignored.en"))
    }

    @Test
    fun reportsCustomSourceAtThePublicResolverBoundary() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """{"flavors":{"prod":{}},"value":"base"}""",
                schema = schema,
                customConfigJson = """{"flavors":{"prod":{"value":"first","\u0076alue":"second"}}}""",
                defaultConfigSourceName = "base.json",
                customConfigSourceName = "custom.json",
            )
        }

        assertEquals(
            "Invalid JSON in source 'custom.json': " +
                "Duplicate JSON object key 'value' at path '$.flavors.prod.value'.",
            error.message,
        )
    }

    @Test
    fun allowsRepeatedNamesAcrossObjectsAndPreservesLayering() {
        val resolved = resolver.resolve(
            defaultConfigJson = """
                {
                  "value": "base",
                  "targets": {"jvm": {"value": "target"}},
                  "flavors": {"prod": {"value": "prod"}, "dev": {}}
                }
            """.trimIndent(),
            schema = schema,
            customConfigJson = """{"flavors":{"prod":{"\u0076alue":"custom"}}}""",
        )

        assertEquals(ConfigValue.StringValue("custom"), resolved.flavors.getValue("prod")[value])
        assertEquals(ConfigValue.StringValue("base"), resolved.flavors.getValue("dev")[value])
    }

    @Test
    fun preservesStringsEscapesNumbersAndSeparateArrayObjects() {
        val result = parser.parseRootEither(
            configText = """
                {
                  "text": "{\"value\":1,\"value\":2}, [ ] :",
                  "escapes": "\"\\\/\b\f\n\r\t\u0041\uD83D\uDE03",
                  "escaped\u005fkey": "decoded",
                  "values": [0, -42, 2147483648, 6.02e23, -1.25E-3, true, false, null],
                  "items": [{"same":"one"}, {"same":"two"}],
                  "nested": {"same": {"same": "three"}},
                  "value": "first",
                  "Value": "second",
                  "\\u0076alue": "literal backslash"
                }
            """.trimIndent(),
            sourceName = "valid.json",
        )

        val entries = assertIs<Either.Right<ConfigNode.ObjectNode>>(result).value.entries
        assertEquals(ConfigNode.StringNode("{\"value\":1,\"value\":2}, [ ] :"), entries.getValue("text"))
        assertEquals(ConfigNode.StringNode("\"\\/\b\u000c\n\r\tA😃"), entries.getValue("escapes"))
        assertEquals(ConfigNode.StringNode("decoded"), entries.getValue("escaped_key"))
        assertEquals(
            ConfigNode.ListNode(
                listOf(
                    ConfigNode.IntNode(0),
                    ConfigNode.IntNode(-42),
                    ConfigNode.LongNode(2147483648),
                    ConfigNode.DoubleNode(6.02e23),
                    ConfigNode.DoubleNode(-1.25e-3),
                    ConfigNode.BooleanNode(true),
                    ConfigNode.BooleanNode(false),
                    ConfigNode.NullNode,
                ),
            ),
            entries.getValue("values"),
        )
        assertEquals(
            ConfigNode.ListNode(
                listOf(
                    ConfigNode.ObjectNode(mapOf("same" to ConfigNode.StringNode("one"))),
                    ConfigNode.ObjectNode(mapOf("same" to ConfigNode.StringNode("two"))),
                ),
            ),
            entries.getValue("items"),
        )
    }

    private fun duplicateError(text: String): ConfigError.InvalidConfigSyntax = when (
        val result = parser.parseRootEither(text, sourceName = "base.json")
    ) {
        is Either.Left -> assertIs<ConfigError.InvalidConfigSyntax>(result.value)
        is Either.Right -> fail("Expected a duplicate-key syntax error for $text")
    }
}
