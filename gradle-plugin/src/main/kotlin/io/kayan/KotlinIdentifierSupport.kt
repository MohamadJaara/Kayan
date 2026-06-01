package io.kayan

internal fun isKotlinIdentifier(value: String): Boolean =
    DefaultConfigResolver.IDENTIFIER_SEGMENT.matches(value) && value !in KOTLIN_HARD_KEYWORDS

internal fun isKotlinQualifiedName(value: String): Boolean = value.split('.').all(::isKotlinIdentifier)

private val KOTLIN_HARD_KEYWORDS: Set<String> = setOf(
    "as",
    "break",
    "class",
    "continue",
    "do",
    "else",
    "false",
    "for",
    "fun",
    "if",
    "in",
    "interface",
    "is",
    "null",
    "object",
    "package",
    "return",
    "super",
    "this",
    "throw",
    "true",
    "try",
    "typealias",
    "typeof",
    "val",
    "var",
    "when",
    "while",
)
