package io.kayan

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JsonSyntaxValidationTest {
    private val parser = JsonConfigFormatParser()

    @Test
    fun rejectsUnquotedStringsAndMalformedNumbersAndLiterals() {
        val tokens = listOf(
            "bareword", "truee", "falsehood", "nul", "nullsuffix", "TRUE", "False", "NULL",
            "+1", "00", "01", "-01", "0x10", "0b1", "1_000", "1.", ".5", "-.5", "-",
            "1e", "1e+", "1e-", "1e+-2", "1E--2", "1e2.3", "1e2e3", "1f", "1d", "١",
            "NaN", "Infinity", "-Infinity",
        )
        tokens.forEach { token ->
            assertInvalidSyntax("""{"value":$token}""")
            assertInvalidSyntax("""{"nested":[{"value":$token}]}""")
        }
    }

    @Test
    fun rejectsUnescapedControlCharactersAndInvalidEscapes() {
        ('\u0000'..'\u001F').forEach { character ->
            assertInvalidSyntax("{\"value\":\"before${character}after\"}")
        }
        listOf("\\x41", "\\v", "\\u12", "\\uGGGG", "\\U00000041").forEach { escape ->
            assertInvalidSyntax("""{"value":"$escape"}""")
            assertInvalidSyntax("""{"$escape":true}""")
        }
        assertInvalidSyntax("""{"value":"unterminated}""")
        assertInvalidSyntax("""{"value":'single quotes'}""")
    }

    @Test
    fun rejectsNonJsonWhitespaceAndMalformedStructure() {
        listOf('\u000B', '\u000C', '\u00A0', '\u2028', '\uFEFF').forEach { whitespace ->
            assertInvalidSyntax("$whitespace{}")
            assertInvalidSyntax("""{"value":${whitespace}true}""")
            assertInvalidSyntax("{}$whitespace")
        }
        listOf(
            "", " \t\r\n", "{", """{"value":}""", "{value:1}", "{true:1}",
            """{"value" 1}""", """{"value":1,}""", """{"value":[1,]}""",
            """{"value":1 "other":2}""", """{"value":[1 2]}""", "{} {}",
            """{"value":true false}""", """{"value":/*comment*/1}""",
        ).forEach(::assertInvalidSyntax)
    }

    @Test
    fun preservesQuotedStringsEscapesUnicodeAndJsonWhitespace() {
        val root = parse(
            " \t\r\n" + """
                {
                  "\u0076alue": "\"\\\/\b\f\n\r\t\u0041\u00e9\uD83D\uDE00",
                  "unicode": "雪 العربية 😀",
                  "strings": ["", "bareword", "+1", "01", "1e", "null", "NaN"]
                }
            """.trimIndent() + "\r\n\t ",
        )

        assertEquals(ConfigNode.StringNode("\"\\/\b\u000C\n\r\tAé😀"), root.entries["value"])
        assertEquals(ConfigNode.StringNode("雪 العربية 😀"), root.entries["unicode"])
        assertEquals(
            ConfigNode.ListNode(listOf("", "bareword", "+1", "01", "1e", "null", "NaN").map(ConfigNode::StringNode)),
            root.entries["strings"],
        )
    }

    @Test
    fun preservesSupportedNumericAndLiteralNodes() {
        val expected = mapOf(
            "0" to ConfigNode.IntNode(0),
            "-0" to ConfigNode.IntNode(0),
            "-42" to ConfigNode.IntNode(-42),
            "2147483647" to ConfigNode.IntNode(Int.MAX_VALUE),
            "2147483648" to ConfigNode.LongNode(2147483648L),
            "-9223372036854775808" to ConfigNode.LongNode(Long.MIN_VALUE),
            "9223372036854775807" to ConfigNode.LongNode(Long.MAX_VALUE),
            "9223372036854775808" to ConfigNode.DoubleNode(9223372036854775808.0),
            "0.25" to ConfigNode.DoubleNode(0.25),
            "-0.0" to ConfigNode.DoubleNode(-0.0),
            "1e2" to ConfigNode.IntNode(100),
            "1E+2" to ConfigNode.IntNode(100),
            "1e10" to ConfigNode.LongNode(10000000000L),
            "-2.5e-2" to ConfigNode.DoubleNode(-0.025),
            "1.7976931348623157e308" to ConfigNode.DoubleNode(Double.MAX_VALUE),
            "true" to ConfigNode.BooleanNode(true),
            "false" to ConfigNode.BooleanNode(false),
            "null" to ConfigNode.NullNode,
        )
        expected.forEach { (token, node) ->
            assertEquals(node, parse("""{"value":$token}""").entries["value"], token)
        }
    }

    @Test
    fun grammarValidationDoesNotApplyDuplicateMemberPolicy() {
        val text = """{"value":1,"value":2,"nested":{"value":3}}"""

        assertIs<Either.Right<Unit>>(validateJsonSyntaxEither(text, "duplicate.json"))
    }

    @Test
    fun rejectsMalformedTokensBeforeTreeParsingCanOverwriteThem() {
        assertInvalidSyntax("""{"value":bareword,"value":"quoted"}""")
        assertInvalidSyntax("""{"value":01,"value":1}""")
    }

    private fun parse(text: String): ConfigNode.ObjectNode =
        assertIs<Either.Right<ConfigNode.ObjectNode>>(parser.parseRootEither(text, "valid.json")).value

    private fun assertInvalidSyntax(text: String) {
        val result = assertIs<Either.Left<ConfigError>>(parser.parseRootEither(text, "broken.json"), text)
        val error = assertIs<ConfigError.InvalidConfigSyntax>(result.value, text)
        assertEquals("broken.json", error.sourceName)
        assertEquals("JSON", error.formatName)
        assertTrue(error.detail.orEmpty().contains("offset"), error.detail)
    }
}
