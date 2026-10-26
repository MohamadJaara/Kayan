package io.kayan

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Scans the original text after JSON parsing succeeds, before using the collapsed object tree.
 * Only checks key uniqueness; JSON grammar and primitive validation belong to the format parser.
 */
internal class JsonDuplicateKeyValidator(private val text: String, private val json: Json, sourceName: String) {
    private val rootContext = DiagnosticContext(sourceName)
    private val scopes = ArrayDeque<Scope>()
    private var offset = 0

    fun validateEither(): Either<ConfigError, Unit> = either {
        while (offset < text.length) {
            val scope = scopes.lastOrNull()
            when (text[offset]) {
                '{' -> scopes.addLast(ObjectScope(scope?.valueContext() ?: rootContext))

                '[' -> scopes.addLast(ArrayScope(scope?.valueContext() ?: rootContext))

                '}', ']' -> scopes.removeLast()

                ',' -> scope?.nextEntry()

                '"' -> {
                    val start = offset
                    skipString()
                    if (scope is ObjectScope && scope.isExpectingKey) {
                        val key = json.parseToJsonElement(text.substring(start, offset + 1)).jsonPrimitive.content
                        scope.key = key
                        scope.isExpectingKey = false
                        if (!scope.keys.add(key)) {
                            raise(
                                ConfigError.InvalidConfigSyntax(
                                    sourceName = rootContext.sourceName,
                                    formatName = "JSON",
                                    detail = "Duplicate JSON object key '$key' at path '${scope.valueContext().path}'.",
                                    cause = null,
                                ),
                            )
                        }
                    }
                }

                else -> Unit
            }
            offset++
        }
    }

    private fun skipString() {
        offset++
        while (offset < text.length && text[offset] != '"') {
            // Skip escaped quotes and backslashes without interpreting punctuation inside strings.
            offset += if (text[offset] == '\\') 2 else 1
        }
    }

    private sealed class Scope(val context: DiagnosticContext) {
        abstract fun valueContext(): DiagnosticContext

        abstract fun nextEntry()
    }

    private class ObjectScope(context: DiagnosticContext) : Scope(context) {
        val keys = mutableSetOf<String>()
        var key: String = ""
        var isExpectingKey = true

        override fun valueContext(): DiagnosticContext = context.atKey(key)

        override fun nextEntry() {
            isExpectingKey = true
        }
    }

    private class ArrayScope(context: DiagnosticContext) : Scope(context) {
        private var index = 0

        override fun valueContext(): DiagnosticContext = context.atIndex(index)

        override fun nextEntry() {
            index++
        }
    }
}
