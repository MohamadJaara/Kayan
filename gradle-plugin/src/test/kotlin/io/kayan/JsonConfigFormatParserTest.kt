package io.kayan

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class JsonConfigFormatParserTest {
    private val parser = JsonConfigFormatParser()

    @Test
    fun parseRootEitherPreservesQuotedScalarsAsStrings() {
        when (
            val result = parser.parseRootEither(
                configText = """
                    {
                      "flavors": {
                        "dev": {
                          "bundle_id": "com.example.dev"
                        }
                      },
                      "empty_value": "",
                      "quoted_numeric_string": "123",
                      "normal_string": "abc",
                      "unquoted_number": 123
                    }
                """.trimIndent(),
                sourceName = "default.json",
            )
        ) {
            is Either.Left -> fail("Expected JSON to parse successfully.")
            is Either.Right -> {
                assertEquals(
                    ConfigNode.StringNode(""),
                    result.value.entries.getValue("empty_value"),
                )
                assertEquals(
                    ConfigNode.StringNode("123"),
                    result.value.entries.getValue("quoted_numeric_string"),
                )
                assertEquals(
                    ConfigNode.StringNode("abc"),
                    result.value.entries.getValue("normal_string"),
                )
                assertEquals(
                    ConfigNode.IntNode(123),
                    result.value.entries.getValue("unquoted_number"),
                )
            }
        }
    }
}
