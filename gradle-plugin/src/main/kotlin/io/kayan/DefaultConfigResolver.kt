package io.kayan

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.kayan.gradle.ExperimentalKayanGradleApi

@Suppress("TooManyFunctions")
public class DefaultConfigResolver : ConfigResolver {
    private val parser: ConfigFormatParser

    public constructor() {
        parser = JsonConfigFormatParser()
    }

    @OptIn(ExperimentalKayanGradleApi::class)
    public constructor(configFormat: ConfigFormat) {
        require(configFormat != ConfigFormat.AUTO) {
            "DefaultConfigResolver requires JSON or YAML when constructed with an explicit ConfigFormat."
        }
        parser = parserFor(configFormat)
    }

    internal constructor(parser: ConfigFormatParser) {
        this.parser = parser
    }

    override fun parse(
        configJson: String,
        schema: ConfigSchema,
    ): AppConfigFile = parseEither(
        configJson = configJson,
        schema = schema,
        sourceName = DEFAULT_PARSE_SOURCE_NAME,
    ).getOrElse { throw it.toConfigValidationException() }

    public fun parse(
        configJson: String,
        schema: ConfigSchema,
        sourceName: String,
    ): AppConfigFile = parseEither(
        configJson = configJson,
        schema = schema,
        sourceName = sourceName,
    ).getOrElse { throw it.toConfigValidationException() }

    override fun resolve(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String?,
    ): ResolvedConfigsByFlavor = resolveEither(
        defaultConfigJson = defaultConfigJson,
        schema = schema,
        customConfigJson = customConfigJson,
        defaultConfigSourceName = DEFAULT_BASE_SOURCE_NAME,
        customConfigSourceName = DEFAULT_CUSTOM_SOURCE_NAME,
    ).getOrElse { throw it.toConfigValidationException() }

    public fun resolve(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String?,
        defaultConfigSourceName: String,
        customConfigSourceName: String = DEFAULT_CUSTOM_SOURCE_NAME,
    ): ResolvedConfigsByFlavor = resolveEither(
        defaultConfigJson = defaultConfigJson,
        schema = schema,
        customConfigJson = customConfigJson,
        defaultConfigSourceName = defaultConfigSourceName,
        customConfigSourceName = customConfigSourceName,
    ).getOrElse { throw it.toConfigValidationException() }

    internal fun parseEither(
        configJson: String,
        schema: ConfigSchema,
        sourceName: String,
    ): Either<ConfigError, AppConfigFile> = parseInternalEither(
        configJson = configJson,
        schema = schema,
        sourceName = sourceName,
    )

    internal fun resolveEither(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String?,
        defaultConfigSourceName: String,
        customConfigSourceName: String = DEFAULT_CUSTOM_SOURCE_NAME,
    ): Either<ConfigError, ResolvedConfigsByFlavor> = resolveInternalEither(
        defaultConfigJson = defaultConfigJson,
        schema = schema,
        customConfigJson = customConfigJson,
        defaultConfigSourceName = defaultConfigSourceName,
        customConfigSourceName = customConfigSourceName,
    )

    private fun parseInternalEither(
        configJson: String,
        schema: ConfigSchema,
        sourceName: String,
    ): Either<ConfigError, AppConfigFile> = either {
        val rootContext = DiagnosticContext(sourceName = sourceName)
        val root = parser.parseRootEither(configJson, sourceName).bind()
        val flavorsLocation = rootContext.atKey(FLAVORS_KEY)
        val flavorsNode = root.entries[FLAVORS_KEY]
            ?: raise(ConfigError.MissingRequiredFlavorsObject(flavorsLocation))
        val flavorsObject = flavorsNode as? ConfigNode.ObjectNode
            ?: raise(
                invalidTypeError(
                    subject = "value for key '$FLAVORS_KEY'",
                    expectedType = "object",
                    actualNode = flavorsNode,
                    context = flavorsLocation,
                ),
            )

        val defaults = parseSectionEither(
            schema = schema,
            objectNode = ConfigNode.ObjectNode(root.entries.filterKeys { it != FLAVORS_KEY }),
            context = rootContext,
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
                    ).bind(),
                )
            }
        }

        AppConfigFile(defaults = defaults, flavors = flavors)
    }

    private fun resolveInternalEither(
        defaultConfigJson: String,
        schema: ConfigSchema,
        customConfigJson: String?,
        defaultConfigSourceName: String,
        customConfigSourceName: String,
    ): Either<ConfigError, ResolvedConfigsByFlavor> = either {
        val defaultConfig = parseInternalEither(defaultConfigJson, schema, defaultConfigSourceName).bind()
        val customConfig = customConfigJson?.let {
            parseInternalEither(it, schema, customConfigSourceName).bind()
        }
        validateCustomConfigEither(
            defaultConfig = defaultConfig,
            customConfig = customConfig,
            defaultConfigSourceName = defaultConfigSourceName,
            customConfigSourceName = customConfigSourceName,
        ).bind()

        ResolvedConfigsByFlavor(
            resolveFlavorsEither(
                schema = schema,
                defaultConfig = defaultConfig,
                customConfig = customConfig,
            ).bind(),
        )
    }

    private fun parseSectionEither(
        schema: ConfigSchema,
        objectNode: ConfigNode.ObjectNode,
        context: DiagnosticContext,
    ): Either<ConfigError, ConfigSection> = either {
        val values = buildMap<ConfigDefinition, ConfigValue> {
            for ((jsonKey, node) in objectNode.entries) {
                val valueContext = context.atKey(jsonKey)
                val definition = schema.definitionFor(jsonKey)
                    ?: raise(unknownKeyError(jsonKey, schema, valueContext))
                put(definition, parseValueEither(definition, node, valueContext).bind())
            }
        }

        ConfigSection(values)
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
            raise(
                invalidTypeError(
                    subject = "value for key '${definition.jsonKey}'",
                    expectedType = expectedType(definition),
                    actualNode = node,
                    context = context,
                ),
            )
        }

        when (definition.kind) {
            ConfigValueKind.STRING -> {
                val value = (node as? ConfigNode.StringNode)?.value
                    ?: raise(
                        invalidTypeError(
                            subject = "value for key '${definition.jsonKey}'",
                            expectedType = expectedType(definition),
                            actualNode = node,
                            context = context,
                        ),
                    )
                ConfigValue.StringValue(value)
            }

            ConfigValueKind.BOOLEAN -> {
                val value = (node as? ConfigNode.BooleanNode)?.value
                    ?: raise(
                        invalidTypeError(
                            subject = "value for key '${definition.jsonKey}'",
                            expectedType = expectedType(definition),
                            actualNode = node,
                            context = context,
                        ),
                    )
                ConfigValue.BooleanValue(value)
            }

            ConfigValueKind.INT -> {
                val value = (node as? ConfigNode.IntNode)?.value
                    ?: raise(
                        invalidTypeError(
                            subject = "value for key '${definition.jsonKey}'",
                            expectedType = expectedType(definition),
                            actualNode = node,
                            context = context,
                        ),
                    )
                ConfigValue.IntValue(value)
            }

            ConfigValueKind.LONG -> {
                val value = when (node) {
                    is ConfigNode.IntNode -> node.value.toLong()
                    is ConfigNode.LongNode -> node.value
                    else -> null
                } ?: raise(
                    invalidTypeError(
                        subject = "value for key '${definition.jsonKey}'",
                        expectedType = expectedType(definition),
                        actualNode = node,
                        context = context,
                    ),
                )
                ConfigValue.LongValue(value)
            }

            ConfigValueKind.DOUBLE -> {
                val value = when (node) {
                    is ConfigNode.IntNode -> node.value.toDouble()
                    is ConfigNode.LongNode -> node.value.toDouble()
                    is ConfigNode.DoubleNode -> node.value
                    else -> null
                } ?: raise(
                    invalidTypeError(
                        subject = "value for key '${definition.jsonKey}'",
                        expectedType = expectedType(definition),
                        actualNode = node,
                        context = context,
                    ),
                )
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
                    ?: raise(
                        invalidTypeError(
                            subject = "value for key '${definition.jsonKey}'",
                            expectedType = expectedType(definition),
                            actualNode = node,
                            context = context,
                        ),
                    )
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

    internal companion object {
        internal const val FLAVORS_KEY: String = "flavors"
        internal const val RESOLVED_CONFIG_SOURCE_NAME: String = "resolved config"
        private const val DEFAULT_PARSE_SOURCE_NAME: String = "config"
        private const val DEFAULT_BASE_SOURCE_NAME: String = "default config"
        private const val DEFAULT_CUSTOM_SOURCE_NAME: String = "custom config"
        internal val IDENTIFIER_SEGMENT: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")

        private fun StringBuilder.shouldInsertEnumSeparator(
            character: Char,
            previous: Char?,
        ): Boolean = character.isUpperCase() &&
            isNotEmpty() &&
            previous?.isLowerCase() == true &&
            last() != '_'
    }
}

private fun validateCustomConfigEither(
    defaultConfig: AppConfigFile,
    customConfig: AppConfigFile?,
    defaultConfigSourceName: String,
    customConfigSourceName: String,
): Either<ConfigError, Unit> = either {
    customConfig?.flavors?.keys?.forEach { customFlavor ->
        if (customFlavor !in defaultConfig.flavors) {
            raise(
                ConfigError.UnknownFlavorInCustomConfig(
                    customFlavor = customFlavor,
                    customContext = DiagnosticContext(customConfigSourceName).atFlavor(customFlavor),
                    defaultConfigSourceName = defaultConfigSourceName,
                ),
            )
        }
    }

    customConfig?.let {
        validateProtectedCustomOverridesEither(
            customConfig = it,
            customConfigSourceName = customConfigSourceName,
            defaultConfigSourceName = defaultConfigSourceName,
        ).bind()
    }
}

private fun resolveFlavorsEither(
    schema: ConfigSchema,
    defaultConfig: AppConfigFile,
    customConfig: AppConfigFile?,
): Either<ConfigError, Map<String, ResolvedFlavorConfig>> = either {
    buildMap {
        for ((flavorName, defaultFlavorValues) in defaultConfig.flavors) {
            put(
                flavorName,
                resolveFlavorEither(
                    flavorName = flavorName,
                    schema = schema,
                    defaultFlavorValues = defaultFlavorValues,
                    defaultConfig = defaultConfig,
                    customConfig = customConfig,
                ).bind(),
            )
        }
    }
}

private fun resolveFlavorEither(
    flavorName: String,
    schema: ConfigSchema,
    defaultFlavorValues: ConfigSection,
    defaultConfig: AppConfigFile,
    customConfig: AppConfigFile?,
): Either<ConfigError, ResolvedFlavorConfig> = either {
    val customFlavorValues = customConfig?.flavors?.get(flavorName)
    val merged = buildMap<ConfigDefinition, ConfigValue?> {
        for (definition in schema.entries) {
            put(
                definition,
                resolvedValue(
                    definition = definition,
                    customFlavorValues = customFlavorValues,
                    customConfig = customConfig,
                    defaultFlavorValues = defaultFlavorValues,
                    defaultConfig = defaultConfig,
                ),
            )
        }
    }

    requireResolvedRequiredKeysEither(
        flavorName = flavorName,
        schema = schema,
        merged = merged,
    ).bind()

    ResolvedFlavorConfig(
        flavorName = flavorName,
        values = merged,
    )
}

private fun requireResolvedRequiredKeysEither(
    flavorName: String,
    schema: ConfigSchema,
    merged: Map<ConfigDefinition, ConfigValue?>,
): Either<ConfigError, Unit> = either {
    schema.entries
        .filter(ConfigDefinition::required)
        .forEach { definition ->
            if (merged[definition] == null || merged[definition] is ConfigValue.NullValue) {
                raise(
                    ConfigError.MissingRequiredResolvedKey(
                        flavorName = flavorName,
                        definition = definition,
                    ),
                )
            }
        }
}

private fun resolvedValue(
    definition: ConfigDefinition,
    customFlavorValues: ConfigSection?,
    customConfig: AppConfigFile?,
    defaultFlavorValues: ConfigSection,
    defaultConfig: AppConfigFile,
): ConfigValue? {
    val sections = listOf(
        customFlavorValues,
        customConfig?.defaults,
        defaultFlavorValues,
        defaultConfig.defaults,
    )

    sections.forEach { section ->
        if (section?.values?.containsKey(definition) == true) {
            return section.values.getValue(definition)
        }
    }

    return null
}

private fun validateProtectedCustomOverridesEither(
    customConfig: AppConfigFile,
    customConfigSourceName: String,
    defaultConfigSourceName: String,
): Either<ConfigError, Unit> = either {
    customConfig.defaults.values.keys.forEach { definition ->
        if (definition.preventOverride) {
            raise(
                ConfigError.PreventedCustomOverride(
                    definition = definition,
                    customContext = DiagnosticContext(customConfigSourceName).atKey(definition.jsonKey),
                    defaultConfigSourceName = defaultConfigSourceName,
                ),
            )
        }
    }

    customConfig.flavors.forEach { (flavorName, section) ->
        section.values.keys.forEach { definition ->
            if (definition.preventOverride) {
                raise(
                    ConfigError.PreventedCustomOverride(
                        definition = definition,
                        customContext = DiagnosticContext(customConfigSourceName)
                            .atFlavor(flavorName)
                            .atKey(definition.jsonKey),
                        defaultConfigSourceName = defaultConfigSourceName,
                    ),
                )
            }
        }
    }
}
