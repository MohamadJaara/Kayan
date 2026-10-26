@file:OptIn(io.kayan.ExperimentalKayanApi::class)

package io.kayan.gradle

import io.kayan.FlavorResolutionTrace
import kotlinx.serialization.json.JsonPrimitive

internal fun renderConfigExplanation(trace: FlavorResolutionTrace): String = buildString {
    val resolved = trace.resolved
    append("Kayan configuration: flavor=")
    append(JsonPrimitive(resolved.flavorName))
    append(", target=")
    append(resolved.targetName?.let(::JsonPrimitive) ?: "<none>")
    resolved.values.forEach { (definition, value) ->
        appendLine()
        append(JsonPrimitive(definition.jsonKey))
        append(" = ")
        append(value?.toJsonElement() ?: "<absent>")
        val source = trace.sources[definition]
        if (source == null) {
            appendLine()
            append("  source: <none>")
        } else {
            appendLine()
            append("  source: ")
            append(JsonPrimitive(source.context.sourceName))
            appendLine()
            append("  layer: ")
            append(source.layer.description)
            if (source.context.targetName != null) {
                append(" target overlay")
            }
            appendLine()
            append("  path: ")
            append(JsonPrimitive(source.context.path))
        }
    }
}
