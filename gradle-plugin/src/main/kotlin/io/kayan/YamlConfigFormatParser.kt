package io.kayan

import arrow.core.Either
import arrow.core.raise.either
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import java.io.StringReader

internal class YamlConfigFormatParser : ConfigFormatParser {
    override val formatName: String = "YAML"

    override fun parseRootEither(
        configText: String,
        sourceName: String,
    ): Either<ConfigError, ConfigNode.ObjectNode> = either {
        val rootNode = try {
            parseDocument(configText)
        } catch (error: YAMLException) {
            raise(
                ConfigError.InvalidConfigSyntax(
                    sourceName = sourceName,
                    formatName = formatName,
                    detail = error.message,
                    cause = error,
                ),
            )
        }

        rootNode as? ConfigNode.ObjectNode ?: raise(
            ConfigError.InvalidType(
                subject = "configuration root",
                expectedType = "object",
                actualType = rootNode.describeType(),
                context = DiagnosticContext(sourceName = sourceName),
            ),
        )
    }

    private fun parseDocument(configText: String): ConfigNode {
        val loaderOptions = LoaderOptions().apply {
            setAllowDuplicateKeys(false)
            setAllowRecursiveKeys(false)
            setMaxAliasesForCollections(0)
        }
        val yaml = Yaml(loaderOptions)
        val documents = yaml.composeAll(StringReader(configText)).toList()

        if (documents.size > 1) {
            throw YAMLException("Multiple YAML documents are not supported.")
        }

        return documents.singleOrNull()?.let(::toConfigNode) ?: ConfigNode.NullNode
    }

    private fun toConfigNode(node: Node): ConfigNode = when (node) {
        is MappingNode -> toObjectNode(node)
        is SequenceNode -> ConfigNode.ListNode(node.value.map(::toConfigNode))
        is ScalarNode -> toScalarNode(node)
        else -> throw YAMLException("Unsupported YAML node type '${node.nodeId}'.")
    }

    private fun toObjectNode(node: MappingNode): ConfigNode.ObjectNode = ConfigNode.ObjectNode(
        node.value.associate { entry ->
            if (entry.keyNode.tag == Tag.MERGE) {
                throw YAMLException("YAML merge keys are not supported.")
            }

            val key = (toConfigNode(entry.keyNode) as? ConfigNode.StringNode)?.value
                ?: throw YAMLException("YAML mappings must use string keys.")

            key to toConfigNode(entry.valueNode)
        },
    )

    private fun toScalarNode(node: ScalarNode): ConfigNode = when (node.tag) {
        Tag.STR -> ConfigNode.StringNode(node.value)
        Tag.BOOL -> parseBooleanNode(node.value)
        Tag.INT -> parseIntegerNode(node.value)
        Tag.FLOAT -> parseDoubleNode(node.value)
        Tag.NULL -> ConfigNode.NullNode
        else -> throw YAMLException(
            "Unsupported YAML scalar tag '${node.tag.value}'. Quote the value if it should be treated as a string.",
        )
    }

    private fun parseBooleanNode(rawValue: String): ConfigNode.BooleanNode {
        val normalized = rawValue.trim().lowercase()

        return when (normalized) {
            "true" -> ConfigNode.BooleanNode(true)
            "false" -> ConfigNode.BooleanNode(false)
            else -> throw YAMLException("Unsupported YAML boolean literal '$rawValue'. Use true or false.")
        }
    }

    private fun parseIntegerNode(rawValue: String): ConfigNode {
        val normalized = rawValue.trim().replace("_", "")
        if (!DECIMAL_INTEGER.matches(normalized)) {
            throw YAMLException("Unsupported YAML integer literal '$rawValue'. Use a base-10 integer.")
        }

        return normalized.toIntOrNull()?.let(ConfigNode::IntNode)
            ?: normalized.toLongOrNull()?.let(ConfigNode::LongNode)
            ?: throw YAMLException(
                "YAML integer literal '$rawValue' is out of range for supported int/long values.",
            )
    }

    private fun parseDoubleNode(rawValue: String): ConfigNode.DoubleNode {
        val normalized = rawValue.trim().replace("_", "")
        if (SPECIAL_FLOAT_LITERALS.contains(normalized.lowercase())) {
            throw YAMLException("Unsupported YAML floating-point literal '$rawValue'.")
        }

        return normalized.toDoubleOrNull()?.let(ConfigNode::DoubleNode)
            ?: throw YAMLException("Unsupported YAML floating-point literal '$rawValue'.")
    }

    private companion object {
        private val DECIMAL_INTEGER: Regex = Regex("[-+]?[0-9]+")
        private val SPECIAL_FLOAT_LITERALS: Set<String> = setOf(".inf", "-.inf", "+.inf", ".nan")
    }
}
