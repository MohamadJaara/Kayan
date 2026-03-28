package io.kayan.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

internal fun parseKotlinTypeName(source: String): TypeName = KotlinTypeNameParser(source).parse()

private class KotlinTypeNameParser(
    private val source: String,
) {
    private var index: Int = 0

    fun parse(): TypeName {
        val parsed = parseType()
        skipWhitespace()
        require(isAtEnd()) {
            "Unexpected trailing content in Kotlin type '$source' at index $index."
        }
        return parsed
    }

    private fun parseType(): TypeName {
        skipWhitespace()
        val rawName = parseQualifiedName()
        skipWhitespace()

        var typeName = if (peek() == '<') {
            advance()
            val arguments = mutableListOf<TypeName>()
            do {
                arguments += parseType()
                skipWhitespace()
            } while (consumeIf(','))
            require(consumeIf('>')) {
                "Unterminated generic type '$source' at index $index."
            }
            KotlinPoetInterop.parameterizedTypeName(
                resolveRawType(rawName),
                arguments,
            )
        } else {
            resolveType(rawName)
        }

        skipWhitespace()
        if (consumeIf('?')) {
            typeName = typeName.copy(nullable = true)
        }
        return typeName
    }

    private fun parseQualifiedName(): String {
        skipWhitespace()
        val start = index
        require(isIdentifierStart(peek())) {
            "Expected Kotlin type name in '$source' at index $index."
        }

        advance()
        while (!isAtEnd()) {
            val next = peek()
            if (isIdentifierPart(next) || next == '.') {
                advance()
            } else {
                break
            }
        }
        return source.substring(start, index)
    }

    private fun resolveType(rawName: String): TypeName = when (rawName) {
        "Boolean",
        "kotlin.Boolean",
        -> ClassName("kotlin", "Boolean")

        "Double",
        "kotlin.Double",
        -> ClassName("kotlin", "Double")

        "Int",
        "kotlin.Int",
        -> ClassName("kotlin", "Int")

        "Long",
        "kotlin.Long",
        -> ClassName("kotlin", "Long")

        "List",
        "kotlin.collections.List",
        -> ClassName("kotlin.collections", "List")

        "Map",
        "kotlin.collections.Map",
        -> ClassName("kotlin.collections", "Map")

        "String",
        "kotlin.String",
        -> ClassName("kotlin", "String")

        else -> ClassName.bestGuess(rawName)
    }

    private fun resolveRawType(rawName: String): ClassName = when (val typeName = resolveType(rawName)) {
        is ClassName -> typeName
        else -> error("Expected class-like raw type for '$rawName'.")
    }

    private fun skipWhitespace() {
        while (!isAtEnd() && peek()?.isWhitespace() == true) {
            index += 1
        }
    }

    private fun consumeIf(character: Char): Boolean =
        if (peek() == character) {
            advance()
            true
        } else {
            false
        }

    private fun advance(): Char = source[index++]

    private fun peek(): Char? = source.getOrNull(index)

    private fun isAtEnd(): Boolean = index >= source.length

    private fun isIdentifierStart(character: Char?): Boolean = character == '_' || character?.isLetter() == true

    private fun isIdentifierPart(character: Char?): Boolean =
        character == '_' || character?.isLetterOrDigit() == true
}
