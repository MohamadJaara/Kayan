package io.kayan

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal interface ConfigFormatParser {
    val formatName: String

    fun parseRootEither(
        configText: String,
        sourceName: String,
    ): Either<ConfigError, ConfigNode.ObjectNode>
}

internal class JsonConfigFormatParser : ConfigFormatParser {
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    override val formatName: String = "JSON"

    override fun parseRootEither(
        configText: String,
        sourceName: String,
    ): Either<ConfigError, ConfigNode.ObjectNode> = either {
        val root = try {
            json.parseToJsonElement(configText)
        } catch (error: SerializationException) {
            raise(
                ConfigError.InvalidConfigSyntax(
                    sourceName = sourceName,
                    formatName = formatName,
                    detail = error.message,
                    cause = error,
                ),
            )
        }

        val rootNode = toConfigNode(root)
        rootNode as? ConfigNode.ObjectNode ?: raise(
            ConfigError.InvalidType(
                subject = "configuration root",
                expectedType = "object",
                actualType = rootNode.describeType(),
                context = DiagnosticContext(sourceName = sourceName),
            ),
        )
    }

    private fun toConfigNode(element: JsonElement): ConfigNode = when (element) {
        is JsonObject -> ConfigNode.ObjectNode(
            element.entries.associate { (key, value) -> key to toConfigNode(value) },
        )

        is JsonArray -> ConfigNode.ListNode(element.map(::toConfigNode))

        is JsonNull -> ConfigNode.NullNode

        is JsonPrimitive -> if (element.isString) {
            ConfigNode.StringNode(requireNotNull(element.contentOrNull))
        } else {
            element.booleanOrNull?.let { ConfigNode.BooleanNode(it) }
                ?: element.intOrNull?.let { ConfigNode.IntNode(it) }
                ?: element.longOrNull?.let { ConfigNode.LongNode(it) }
                ?: element.doubleOrNull?.let { ConfigNode.DoubleNode(it) }
                ?: ConfigNode.StringNode(requireNotNull(element.contentOrNull))
        }
    }
}
