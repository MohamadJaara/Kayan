@file:Suppress("CyclomaticComplexMethod", "ReturnCount", "ThrowsCount", "TooManyFunctions")

package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.kayan.ConfigDefinition
import io.kayan.ConfigValue
import io.kayan.ConfigValueKind
import io.kayan.ResolvedFlavorConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long

internal fun serializeResolvedValues(resolvedFlavorConfig: ResolvedFlavorConfig): String =
    resolvedValueJson.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            resolvedFlavorConfig.values.entries
                .sortedBy { it.key.jsonKey }
                .forEach { (definition, value) ->
                    put(definition.jsonKey, serializeResolvedValue(definition, value))
                }
        },
    )

internal fun deserializeResolvedValues(serialized: String): Map<String, ResolvedBuildValue> =
    deserializeResolvedValuesEither(serialized).getOrElse { throw it.toGradleException() }

internal fun deserializeResolvedValuesEither(
    serialized: String,
): Either<BuildTimeAccessError, Map<String, ResolvedBuildValue>> {
    val root = try {
        resolvedValueJson.parseToJsonElement(serialized)
    } catch (error: SerializationException) {
        return BuildTimeAccessError.InvalidSerializedResolvedValuesJson(error).left()
    }

    val values = root as? JsonObject ?: return BuildTimeAccessError.InvalidSerializedResolvedValuesRoot.left()
    val resolvedValues = linkedMapOf<String, ResolvedBuildValue>()

    for ((jsonKey, element) in values) {
        when (val resolvedValue = deserializeResolvedValueEither(jsonKey, element)) {
            is Either.Left -> return resolvedValue
            is Either.Right -> resolvedValues[jsonKey] = resolvedValue.value
        }
    }

    return resolvedValues.right()
}

private fun serializeResolvedValue(
    definition: ConfigDefinition,
    value: ConfigValue?,
): JsonObject = buildJsonObject {
    put("kind", JsonPrimitive((value?.kind ?: definition.kind).name))
    put(
        "value",
        when (value) {
            null,
            is ConfigValue.NullValue -> JsonNull
            is ConfigValue.StringValue -> JsonPrimitive(value.value)
            is ConfigValue.BooleanValue -> JsonPrimitive(value.value)
            is ConfigValue.IntValue -> JsonPrimitive(value.value)
            is ConfigValue.LongValue -> JsonPrimitive(value.value)
            is ConfigValue.DoubleValue -> JsonPrimitive(value.value)
            is ConfigValue.StringMapValue -> buildJsonObject {
                value.value.entries
                    .sortedBy { it.key }
                    .forEach { (key, entryValue) ->
                        put(key, JsonPrimitive(entryValue))
                    }
            }
            is ConfigValue.StringListValue -> JsonArray(value.value.map(::JsonPrimitive))
            is ConfigValue.StringListMapValue -> buildJsonObject {
                value.value.entries
                    .sortedBy { it.key }
                    .forEach { (key, entries) ->
                        put(key, JsonArray(entries.map(::JsonPrimitive)))
                    }
            }
            is ConfigValue.EnumValue -> JsonPrimitive(value.value)
        },
    )
}

private fun deserializeResolvedValueEither(
    jsonKey: String,
    element: JsonElement,
): Either<BuildTimeAccessError, ResolvedBuildValue> {
    val entry = element as? JsonObject ?: return BuildTimeAccessError.ResolvedValueNotAnObject(jsonKey).left()
    val kind = when (val result = entry.requireKindEither(jsonKey)) {
        is Either.Left -> return result
        is Either.Right -> result.value
    }
    val valueElement = entry["value"]
        ?: return BuildTimeAccessError.MissingResolvedValueField(jsonKey, "value").left()
    val rawValue = when (val result = valueElement.toRawValueEither(jsonKey, kind)) {
        is Either.Left -> return result
        is Either.Right -> result.value
    }

    return ResolvedBuildValue(
        jsonKey = jsonKey,
        kind = kind,
        rawValue = rawValue,
    ).right()
}

private fun JsonObject.requireKindEither(
    jsonKey: String,
): Either<BuildTimeAccessError, ConfigValueKind> {
    val rawKind = (get("kind") as? JsonPrimitive)?.contentOrNull
        ?: return BuildTimeAccessError.MissingResolvedValueField(jsonKey, "kind").left()

    return try {
        ConfigValueKind.valueOf(rawKind).right()
    } catch (error: IllegalArgumentException) {
        BuildTimeAccessError.UnsupportedResolvedValueKind(jsonKey, rawKind, error).left()
    }
}

private fun JsonElement.toRawValueEither(
    jsonKey: String,
    kind: ConfigValueKind,
): Either<BuildTimeAccessError, Any?> {
    if (this is JsonNull) {
        return null.right()
    }

    return when (kind) {
        ConfigValueKind.STRING,
        ConfigValueKind.ENUM -> (this as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
            ?.right()
            ?: invalidResolvedValueType(jsonKey, kind, "string").left()
        ConfigValueKind.BOOLEAN -> decodePrimitiveEither(jsonKey, kind, "boolean") { boolean }
        ConfigValueKind.INT -> decodePrimitiveEither(jsonKey, kind, "int") { int }
        ConfigValueKind.LONG -> decodePrimitiveEither(jsonKey, kind, "long") { long }
        ConfigValueKind.DOUBLE -> decodePrimitiveEither(jsonKey, kind, "double") { double }
        ConfigValueKind.STRING_LIST -> decodeStringListEither(jsonKey, this)
        ConfigValueKind.STRING_MAP -> decodeStringMapEither(jsonKey, this)
        ConfigValueKind.STRING_LIST_MAP -> decodeStringListMapEither(jsonKey, this)
    }
}

private fun invalidResolvedValueType(
    jsonKey: String,
    kind: ConfigValueKind,
    expectedShape: String,
): BuildTimeAccessError.InvalidResolvedValueEncoding =
    BuildTimeAccessError.InvalidResolvedValueEncoding(jsonKey, kind, expectedShape)

private fun decodeStringListEither(
    jsonKey: String,
    element: JsonElement,
): Either<BuildTimeAccessError, List<String>> {
    val entries = element as? JsonArray
        ?: return invalidResolvedValueType(jsonKey, ConfigValueKind.STRING_LIST, "array of strings").left()

    val resolvedEntries = mutableListOf<String>()
    entries.forEachIndexed { index, entry ->
        val resolvedEntry = (entry as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
            ?: return BuildTimeAccessError.InvalidResolvedValueEntry(
                "Resolved value '$jsonKey' entry at index $index must be a string.",
            ).left()
        resolvedEntries += resolvedEntry
    }

    return resolvedEntries.right()
}

private fun <T> JsonElement.decodePrimitiveEither(
    jsonKey: String,
    kind: ConfigValueKind,
    expectedShape: String,
    decode: JsonPrimitive.() -> T,
): Either<BuildTimeAccessError, T> {
    val primitive = this as? JsonPrimitive
        ?: return invalidResolvedValueType(jsonKey, kind, expectedShape).left()

    return runCatching { primitive.decode() }
        .fold(
            onSuccess = { it.right() },
            onFailure = { invalidResolvedValueType(jsonKey, kind, expectedShape).left() },
        )
}

private fun decodeStringMapEither(
    jsonKey: String,
    element: JsonElement,
): Either<BuildTimeAccessError, Map<String, String>> {
    val entries = element as? JsonObject
        ?: return invalidResolvedValueType(jsonKey, ConfigValueKind.STRING_MAP, "object of strings").left()

    val resolvedEntries = linkedMapOf<String, String>()
    for ((entryKey, entryValue) in entries) {
        val resolvedEntry = (entryValue as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
            ?: return BuildTimeAccessError.InvalidResolvedValueEntry(
                "Resolved value '$jsonKey' entry '$entryKey' must be a string.",
            ).left()
        resolvedEntries[entryKey] = resolvedEntry
    }

    return resolvedEntries.right()
}

private fun decodeStringListMapEither(
    jsonKey: String,
    element: JsonElement,
): Either<BuildTimeAccessError, Map<String, List<String>>> {
    val entries = element as? JsonObject
        ?: return invalidResolvedValueType(
            jsonKey,
            ConfigValueKind.STRING_LIST_MAP,
            "object of string arrays",
        ).left()

    val resolvedEntries = linkedMapOf<String, List<String>>()
    for ((entryKey, entryValue) in entries) {
        val valueEntries = entryValue as? JsonArray
            ?: return BuildTimeAccessError.InvalidResolvedValueEntry(
                "Resolved value '$jsonKey' entry '$entryKey' must be an array of strings.",
            ).left()

        val resolvedValueEntries = mutableListOf<String>()
        valueEntries.forEachIndexed { index, item ->
            val resolvedItem = (item as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
                ?: return BuildTimeAccessError.InvalidResolvedValueEntry(
                    "Resolved value '$jsonKey' entry '$entryKey' at index $index must be a string.",
                ).left()
            resolvedValueEntries += resolvedItem
        }

        resolvedEntries[entryKey] = resolvedValueEntries
    }

    return resolvedEntries.right()
}

private val resolvedValueJson: Json = Json {
    ignoreUnknownKeys = false
    isLenient = false
}
