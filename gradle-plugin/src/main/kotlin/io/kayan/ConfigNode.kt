package io.kayan

internal sealed interface ConfigNode {
    data class ObjectNode(
        val entries: Map<String, ConfigNode>,
    ) : ConfigNode

    data class ListNode(
        val items: List<ConfigNode>,
    ) : ConfigNode

    data class StringNode(
        val value: String,
    ) : ConfigNode

    data class BooleanNode(
        val value: Boolean,
    ) : ConfigNode

    data class IntNode(
        val value: Int,
    ) : ConfigNode

    data class LongNode(
        val value: Long,
    ) : ConfigNode

    data class DoubleNode(
        val value: Double,
    ) : ConfigNode

    data object NullNode : ConfigNode
}

internal fun ConfigNode.describeType(): String = when (this) {
    is ConfigNode.ObjectNode -> "object"
    is ConfigNode.ListNode -> "array"
    is ConfigNode.StringNode -> "string"
    is ConfigNode.BooleanNode -> "boolean"
    is ConfigNode.IntNode -> "int"
    is ConfigNode.LongNode -> "long"
    is ConfigNode.DoubleNode -> "double"
    ConfigNode.NullNode -> "null"
}
