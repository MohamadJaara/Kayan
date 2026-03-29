package io.kayan

import arrow.core.Either
import arrow.core.raise.either

internal class ConfigSectionParser(
    private val parser: ConfigFormatParser,
) {
    fun parseEither(
        configJson: String,
        schema: ConfigSchema,
        sourceName: String,
        validationMode: KayanValidationMode,
    ): Either<ConfigError, AppConfigFile> = either {
        val rootContext = DiagnosticContext(sourceName = sourceName)
        val root = parser.parseRootEither(configJson, sourceName).bind()
        val flavorsLocation = rootContext.atKey(DefaultConfigResolver.FLAVORS_KEY)
        val flavorsNode = root.entries[DefaultConfigResolver.FLAVORS_KEY]
            ?: raise(ConfigError.MissingRequiredFlavorsObject(flavorsLocation))
        val flavorsObject = flavorsNode as? ConfigNode.ObjectNode
            ?: raise(
                invalidTypeError(
                    subject = "value for key '${DefaultConfigResolver.FLAVORS_KEY}'",
                    expectedType = "object",
                    actualNode = flavorsNode,
                    context = flavorsLocation,
                ),
            )

        val defaults = parseSectionEither(
            schema = schema,
            objectNode = ConfigNode.ObjectNode(
                root.entries.filterKeys { it != DefaultConfigResolver.FLAVORS_KEY },
            ),
            context = rootContext,
            validationMode = validationMode,
        ).bind()

        val flavors = buildMap<String, ConfigSection> {
            for ((flavorName, flavorNode) in flavorsObject.entries) {
                val flavorContext = rootContext.atFlavor(flavorName)
                val flavorObject = flavorNode as? ConfigNode.ObjectNode
                    ?: raise(
                        invalidTypeError(
                            subject = "value for flavor '$flavorName'",
                            expectedType = "object",
                            actualNode = flavorNode,
                            context = flavorContext,
                        ),
                    )

                put(
                    flavorName,
                    parseSectionEither(
                        schema = schema,
                        objectNode = flavorObject,
                        context = flavorContext,
                        validationMode = validationMode,
                    ).bind(),
                )
            }
        }

        AppConfigFile(defaults = defaults, flavors = flavors)
    }

    private fun parseSectionEither(
        schema: ConfigSchema,
        objectNode: ConfigNode.ObjectNode,
        context: DiagnosticContext,
        validationMode: KayanValidationMode,
        allowTargets: Boolean = true,
    ): Either<ConfigError, ConfigSection> = either {
        var targets: Map<String, ConfigSection> = emptyMap()
        val values = buildMap<ConfigDefinition, ConfigValue> {
            for ((jsonKey, node) in objectNode.entries) {
                if (allowTargets && jsonKey == DefaultConfigResolver.TARGETS_KEY) {
                    val targetsContext = context.atKey(DefaultConfigResolver.TARGETS_KEY)
                    val targetsObject = node as? ConfigNode.ObjectNode
                        ?: raise(
                            invalidTypeError(
                                subject = "value for key '${DefaultConfigResolver.TARGETS_KEY}'",
                                expectedType = "object",
                                actualNode = node,
                                context = targetsContext,
                            ),
                        )
                    targets = buildMap {
                        for ((targetName, targetNode) in targetsObject.entries) {
                            val targetContext = context.atTarget(targetName)
                            val targetObject = targetNode as? ConfigNode.ObjectNode
                                ?: raise(
                                    invalidTypeError(
                                        subject = "value for target '$targetName'",
                                        expectedType = "object",
                                        actualNode = targetNode,
                                        context = targetContext,
                                    ),
                                )

                            put(
                                targetName,
                                parseSectionEither(
                                    schema = schema,
                                    objectNode = targetObject,
                                    context = targetContext,
                                    validationMode = validationMode,
                                    allowTargets = false,
                                ).bind(),
                            )
                        }
                    }
                } else {
                    val valueContext = context.atKey(jsonKey)
                    val definition = schema.definitionFor(jsonKey)
                    if (definition == null) {
                        if (validationMode == KayanValidationMode.STRICT) {
                            raise(unknownKeyError(jsonKey, schema, valueContext))
                        }
                    } else {
                        put(definition, parseValueEither(definition, node, valueContext).bind())
                    }
                }
            }
        }

        ConfigSection(values, targets)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun parseValueEither(
        definition: ConfigDefinition,
        node: ConfigNode,
        context: DiagnosticContext,
    ): Either<ConfigError, ConfigValue> = either {
        if (node is ConfigNode.NullNode) {
            if (definition.nullable) {
                return@either ConfigValue.NullValue(definition.kind)
            }
            raise(invalidValueTypeError(definition, node, context))
        }

        when (definition.kind) {
            ConfigValueKind.STRING -> {
                val value = (node as? ConfigNode.StringNode)?.value
                    ?: raise(invalidValueTypeError(definition, node, context))
                ConfigValue.StringValue(value)
            }

            ConfigValueKind.BOOLEAN -> {
                val value = (node as? ConfigNode.BooleanNode)?.value
                    ?: raise(invalidValueTypeError(definition, node, context))
                ConfigValue.BooleanValue(value)
            }

            ConfigValueKind.INT -> {
                val value = (node as? ConfigNode.IntNode)?.value
                    ?: raise(invalidValueTypeError(definition, node, context))
                ConfigValue.IntValue(value)
            }

            ConfigValueKind.LONG -> {
                val value = when (node) {
                    is ConfigNode.IntNode -> node.value.toLong()
                    is ConfigNode.LongNode -> node.value
                    else -> null
                } ?: raise(invalidValueTypeError(definition, node, context))
                ConfigValue.LongValue(value)
            }

            ConfigValueKind.DOUBLE -> {
                val value = when (node) {
                    is ConfigNode.IntNode -> node.value.toDouble()
                    is ConfigNode.LongNode -> node.value.toDouble()
                    is ConfigNode.DoubleNode -> node.value
                    else -> null
                } ?: raise(invalidValueTypeError(definition, node, context))
                ConfigValue.DoubleValue(value)
            }

            ConfigValueKind.STRING_MAP -> ConfigValue.StringMapValue(
                parseStringMapEither(
                    definition = definition,
                    node = node,
                    context = context,
                ).bind(),
            )

            ConfigValueKind.STRING_LIST -> ConfigValue.StringListValue(
                parseStringListEither(
                    definition = definition,
                    node = node,
                    context = context,
                ).bind(),
            )

            ConfigValueKind.STRING_LIST_MAP -> ConfigValue.StringListMapValue(
                parseStringListMapEither(
                    definition = definition,
                    node = node,
                    context = context,
                ).bind(),
            )

            ConfigValueKind.ENUM -> {
                val value = (node as? ConfigNode.StringNode)?.value
                    ?: raise(invalidValueTypeError(definition, node, context))
                ConfigValue.EnumValue(
                    normalizeEnumConstantEither(
                        jsonKey = definition.jsonKey,
                        value = value,
                        context = context,
                    ).bind(),
                )
            }
        }
    }

    private fun parseStringListEither(
        definition: ConfigDefinition,
        node: ConfigNode,
        context: DiagnosticContext,
        expectedType: String = expectedType(definition),
    ): Either<ConfigError, List<String>> = either {
        val listNode = node as? ConfigNode.ListNode
            ?: raise(
                invalidTypeError(
                    subject = "value for key '${definition.jsonKey}'",
                    expectedType = expectedType,
                    actualNode = node,
                    context = context,
                ),
            )

        buildList {
            listNode.items.forEachIndexed { index, element ->
                val value = (element as? ConfigNode.StringNode)?.value
                    ?: raise(
                        invalidTypeError(
                            subject = "list entry for key '${definition.jsonKey}'",
                            expectedType = "string",
                            actualNode = element,
                            context = context.atIndex(index),
                        ),
                    )
                add(value)
            }
        }
    }

    private fun parseStringListMapEither(
        definition: ConfigDefinition,
        node: ConfigNode,
        context: DiagnosticContext,
    ): Either<ConfigError, Map<String, List<String>>> = either {
        val objectNode = node as? ConfigNode.ObjectNode
            ?: raise(
                invalidTypeError(
                    subject = "value for key '${definition.jsonKey}'",
                    expectedType = expectedType(definition),
                    actualNode = node,
                    context = context,
                ),
            )

        buildMap {
            for ((mapKey, element) in objectNode.entries) {
                put(
                    mapKey,
                    parseStringListEither(
                        definition = definition,
                        node = element,
                        context = context.atKey(mapKey),
                        expectedType = "list of strings",
                    ).bind(),
                )
            }
        }
    }

    private fun parseStringMapEither(
        definition: ConfigDefinition,
        node: ConfigNode,
        context: DiagnosticContext,
    ): Either<ConfigError, Map<String, String>> = either {
        val objectNode = node as? ConfigNode.ObjectNode
            ?: raise(
                invalidTypeError(
                    subject = "value for key '${definition.jsonKey}'",
                    expectedType = expectedType(definition),
                    actualNode = node,
                    context = context,
                ),
            )

        buildMap {
            for ((mapKey, element) in objectNode.entries) {
                val value = (element as? ConfigNode.StringNode)?.value
                    ?: raise(
                        invalidTypeError(
                            subject = "map entry for key '${definition.jsonKey}'",
                            expectedType = "string",
                            actualNode = element,
                            context = context.atKey(mapKey),
                        ),
                    )
                put(mapKey, value)
            }
        }
    }

    private fun unknownKeyError(
        jsonKey: String,
        schema: ConfigSchema,
        context: DiagnosticContext,
    ): ConfigError.UnknownKey = ConfigError.UnknownKey(
        jsonKey = jsonKey,
        context = context,
        suggestions = closeKeyMatches(jsonKey, schema.entries.map(ConfigDefinition::jsonKey)),
    )

    private fun invalidValueTypeError(
        definition: ConfigDefinition,
        node: ConfigNode,
        context: DiagnosticContext,
    ): ConfigError.InvalidType = invalidTypeError(
        subject = "value for key '${definition.jsonKey}'",
        expectedType = expectedType(definition),
        actualNode = node,
        context = context,
    )

    private fun invalidTypeError(
        subject: String,
        expectedType: String,
        actualNode: ConfigNode,
        context: DiagnosticContext,
    ): ConfigError.InvalidType = ConfigError.InvalidType(
        subject = subject,
        expectedType = expectedType,
        actualType = actualNode.describeType(),
        context = context,
    )

    private fun expectedType(definition: ConfigDefinition): String {
        val rawType = when (definition.kind) {
            ConfigValueKind.STRING -> "string"
            ConfigValueKind.BOOLEAN -> "boolean"
            ConfigValueKind.INT -> "int"
            ConfigValueKind.LONG -> "long"
            ConfigValueKind.DOUBLE -> "double"
            ConfigValueKind.STRING_MAP -> "object mapping strings to strings"
            ConfigValueKind.STRING_LIST -> "list of strings"
            ConfigValueKind.STRING_LIST_MAP -> "object mapping strings to lists of strings"
            ConfigValueKind.ENUM -> "string"
        }
        return if (definition.nullable) "$rawType or null" else rawType
    }

    private fun normalizeEnumConstantEither(
        jsonKey: String,
        value: String,
        context: DiagnosticContext,
    ): Either<ConfigError, String> = either {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            raise(
                ConfigError.InvalidEnumValue(
                    jsonKey = jsonKey,
                    context = context,
                    detail = "Enum values must not be blank.",
                ),
            )
        }

        val normalized = buildString {
            trimmed.forEachIndexed { index, character ->
                when {
                    character.isLetterOrDigit() -> {
                        val previous = trimmed.getOrNull(index - 1)
                        if (shouldInsertEnumSeparator(character, previous)) {
                            append('_')
                        }
                        append(character.uppercaseChar())
                    }

                    isNotEmpty() && last() != '_' -> append('_')
                }
            }
        }.trim('_')

        if (normalized.isEmpty()) {
            raise(
                ConfigError.InvalidEnumValue(
                    jsonKey = jsonKey,
                    context = context,
                    detail = "Enum values must contain at least one letter or digit.",
                ),
            )
        }

        normalized
    }

    private fun StringBuilder.shouldInsertEnumSeparator(
        character: Char,
        previous: Char?,
    ): Boolean = character.isUpperCase() &&
        isNotEmpty() &&
        previous?.isLowerCase() == true &&
        last() != '_'
}
