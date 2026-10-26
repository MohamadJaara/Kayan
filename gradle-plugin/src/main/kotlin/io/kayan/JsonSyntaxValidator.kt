package io.kayan

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either

/**
 * Checks the original JSON text before tree parsing can normalize primitive tokens.
 * This pass only enforces grammar; object member uniqueness is a separate policy.
 */
internal fun validateJsonSyntaxEither(configText: String, sourceName: String): Either<ConfigError, Unit> = either {
    JsonSyntaxValidator(configText, sourceName, this).validate()
}

private class JsonSyntaxValidator(
    private val text: String,
    private val sourceName: String,
    private val errors: Raise<ConfigError>,
) {
    private var offset: Int = 0
    private val current: Char?
        get() = text.getOrNull(offset)

    fun validate() {
        readValue()
        skipWhitespace()
        if (offset != text.length) {
            invalid("Expected the end of the JSON document")
        }
    }

    private fun readValue() {
        skipWhitespace()
        when (current) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> readLiteral("true")
            'f' -> readLiteral("false")
            'n' -> readLiteral("null")
            '-', in '0'..'9' -> readNumber()
            else -> invalid("Expected a JSON value (strings must be double-quoted)")
        }
    }

    private fun readObject() {
        expect('{')
        skipWhitespace()
        if (consume('}')) {
            return
        }
        do {
            skipWhitespace()
            readString()
            skipWhitespace()
            expect(':')
            readValue()
            skipWhitespace()
        } while (consume(','))
        expect('}')
    }

    private fun readArray() {
        expect('[')
        skipWhitespace()
        if (consume(']')) {
            return
        }
        do {
            readValue()
            skipWhitespace()
        } while (consume(','))
        expect(']')
    }

    private fun readString() {
        expect('"')
        while (offset < text.length) {
            when (text[offset++]) {
                '"' -> return
                '\\' -> readEscape()
                in '\u0000'..'\u001F' -> invalid("Unescaped control character in JSON string")
            }
        }
        invalid("Unterminated JSON string")
    }

    private fun readEscape() {
        when (current) {
            '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> offset++

            'u' -> {
                offset++
                repeat(UNICODE_ESCAPE_LENGTH) {
                    when (current) {
                        in '0'..'9', in 'a'..'f', in 'A'..'F' -> offset++
                        else -> invalid("Expected four hexadecimal digits after a JSON Unicode escape")
                    }
                }
            }

            else -> invalid("Invalid JSON string escape")
        }
    }

    private fun readNumber() {
        consume('-')
        if (!consume('0')) {
            readDigits()
        }
        if (consume('.')) {
            readDigits()
        }
        if (consume('e') || consume('E')) {
            if (!consume('+')) {
                consume('-')
            }
            readDigits()
        }
    }

    private fun readDigits() {
        val start = offset
        while (current in '0'..'9') {
            offset++
        }
        if (offset == start) {
            invalid("Expected a digit in JSON number")
        }
    }

    private fun readLiteral(literal: String) {
        if (!text.startsWith(literal, offset)) {
            invalid("Expected JSON literal '$literal'")
        }
        offset += literal.length
    }

    private fun skipWhitespace() {
        while (current in WHITESPACE) {
            offset++
        }
    }

    private fun consume(character: Char): Boolean = if (current == character) {
        offset++
        true
    } else {
        false
    }

    private fun expect(character: Char) {
        if (!consume(character)) {
            invalid("Expected '$character'")
        }
    }

    private fun invalid(detail: String): Nothing = errors.raise(
        ConfigError.InvalidConfigSyntax(
            sourceName = sourceName,
            formatName = "JSON",
            detail = "$detail at offset $offset.",
            cause = null,
        ),
    )

    private companion object {
        private const val UNICODE_ESCAPE_LENGTH: Int = 4
        private val WHITESPACE: Set<Char> = setOf(' ', '\t', '\n', '\r')
    }
}
