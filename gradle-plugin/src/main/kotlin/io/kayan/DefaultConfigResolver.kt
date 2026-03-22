package io.kayan

import arrow.core.Either
import arrow.core.getOrElse
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

@Suppress("TooManyFunctions")
public class DefaultConfigResolver : ConfigResolver {
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
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
        val root = parseRootEither(configJson, rootContext).bind()
        val flavorsLocation = rootContext.atKey(FLAVORS_KEY)
        val flavorsElement = root[FLAVORS_KEY] ?: raise(ConfigError.MissingRequiredFlavorsObject(flavorsLocation))
        val flavorsObject = flavorsElement as? JsonObject
            ?: raise(
                invalidTypeError(
                    subject = "value for key '$FLAVORS_KEY'",
                    expectedType = "object",
                    actualElement = flavorsElement,
                    context = flavorsLocation,
                )
            )

        val defaults = parseSectionEither(
            schema = schema,
            jsonObject = JsonObject(root.filterKeys { it != FLAVORS_KEY }),
            context = rootContext,
        ).bind()

        val flavors = buildMap<String, ConfigSection> {
            for ((flavorName, flavorElement) in flavorsObject) {
                val flavorContext = rootContext.atFlavor(flavorName)
                val flavorObject = flavorElement as? JsonObject
                    ?: raise(
                        invalidTypeError(
                            subject = "value for flavor '$flavorName'",
                            expectedType = "object",
                            actualElement = flavorElement,
                            context = flavorContext,
                        )
                    )

                put(
                    flavorName,
                    parseSectionEither(
                        schema = schema,
                        jsonObject = flavorObject,
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

        val resolvedFlavors = buildMap<String, ResolvedFlavorConfig> {
            for ((flavorName, defaultFlavorValues) in defaultConfig.flavors) {
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

                put(
                    flavorName,
                    ResolvedFlavorConfig(
                        flavorName = flavorName,
                        values = merged,
                    ),
                )
            }
        }

        ResolvedConfigsByFlavor(resolvedFlavors)
    }

    private fun parseRootEither(
        configJson: String,
        context: DiagnosticContext,
    ): Either<ConfigError, JsonObject> = either {
        val root = try {
            json.parseToJsonElement(configJson)
        } catch (error: SerializationException) {
            raise(
                ConfigError.InvalidJson(
                    sourceName = context.sourceName,
                    detail = error.message,
                    cause = error,
                ),
            )
        }

        root as? JsonObject ?: raise(
            invalidTypeError(
                subject = "configuration root",
                expectedType = "object",
                actualElement = root,
                context = context,
            )
        )
    }

    private fun parseSectionEither(
        schema: ConfigSchema,
        jsonObject: JsonObject,
        context: DiagnosticContext,
    ): Either<ConfigError, ConfigSection> = either {
        val values = buildMap<ConfigDefinition, ConfigValue> {
            for ((jsonKey, jsonValue) in jsonObject) {
                val valueContext = context.atKey(jsonKey)
                val definition = schema.definitionFor(jsonKey)
                    ?: raise(unknownKeyError(jsonKey, schema, valueContext))
                put(definition, parseValueEither(definition, jsonValue, valueContext).bind())
            }
        }

        ConfigSection(values)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun parseValueEither(
        definition: ConfigDefinition,
        jsonElement: JsonElement,
        context: DiagnosticContext,
    ): Either<ConfigError, ConfigValue> = either {
        if (jsonElement is JsonNull) {
            if (definition.nullable) {
                return@either ConfigValue.NullValue(definition.kind)
            }
            raise(
                invalidTypeError(
                    subject = "value for key '${definition.jsonKey}'",
                    expectedType = expectedType(definition),
                    actualElement = jsonElement,
                    context = context,
                ),
            )
        }

        when (definition.kind) {
            ConfigValueKind.STRING -> {
                val primitive = jsonElement as? JsonPrimitive
                val value = primitive?.takeIf { it.isString }?.contentOrNull
                    ?: raise(
                        invalidTypeError(
                            subject = "value for key '${definition.jsonKey}'",
                            expectedType = expectedType(definition),
                            actualElement = jsonElement,
                            context = context,
                        ),
                    )
                ConfigValue.StringValue(value)
            }

            ConfigValueKind.BOOLEAN -> {
                val primitive = jsonElement as? JsonPrimitive
                val value = primitive?.takeIf { !it.isString }?.booleanOrNull
                    ?: raise(
                        invalidTypeError(
                            subject = "value for key '${definition.jsonKey}'",
                            expectedType = expectedType(definition),
                            actualElement = jsonElement,
                            context = context,
                        ),
                    )
                ConfigValue.BooleanValue(value)
            }

            ConfigValueKind.INT -> {
                val primitive = jsonElement as? JsonPrimitive
                val value = primitive?.takeIf { !it.isString }?.intOrNull
                    ?: raise(
                        invalidTypeError(
                            subject = "value for key '${definition.jsonKey}'",
                            expectedType = expectedType(definition),
                            actualElement = jsonElement,
                            context = context,
                        ),
                    )
                ConfigValue.IntValue(value)
            }

            ConfigValueKind.LONG -> {
                val primitive = jsonElement as? JsonPrimitive
                val value = primitive?.takeIf { !it.isString }?.longOrNull
                    ?: raise(
                        invalidTypeError(
                            subject = "value for key '${definition.jsonKey}'",
                            expectedType = expectedType(definition),
                            actualElement = jsonElement,
                            context = context,
                        ),
                    )
                ConfigValue.LongValue(value)
            }

            ConfigValueKind.DOUBLE -> {
                val primitive = jsonElement as? JsonPrimitive
                val value = primitive?.takeIf { !it.isString }?.doubleOrNull
                    ?: raise(
                        invalidTypeError(
                            subject = "value for key '${definition.jsonKey}'",
                            expectedType = expectedType(definition),
                            actualElement = jsonElement,
                            context = context,
                        ),
                    )
                ConfigValue.DoubleValue(value)
            }

            ConfigValueKind.STRING_MAP -> ConfigValue.StringMapValue(
                parseStringMapEither(
                    definition = definition,
                    jsonElement = jsonElement,
                    context = context,
                ).bind(),
            )

            ConfigValueKind.STRING_LIST -> ConfigValue.StringListValue(
                parseStringListEither(
                    definition = definition,
                    jsonElement = jsonElement,
                    context = context,
                ).bind(),
            )

            ConfigValueKind.STRING_LIST_MAP -> ConfigValue.StringListMapValue(
                parseStringListMapEither(
                    definition = definition,
                    jsonElement = jsonElement,
                    context = context,
                ).bind(),
            )

            ConfigValueKind.ENUM -> {
                val primitive = jsonElement as? JsonPrimitive
                val value = primitive?.takeIf { it.isString }?.contentOrNull
                    ?: raise(
                        invalidTypeError(
                            subject = "value for key '${definition.jsonKey}'",
                            expectedType = expectedType(definition),
                            actualElement = jsonElement,
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
        jsonElement: JsonElement,
        context: DiagnosticContext,
        expectedType: String = expectedType(definition),
    ): Either<ConfigError, List<String>> = either {
        val jsonArray = jsonElement as? JsonArray
            ?: raise(
                invalidTypeError(
                    subject = "value for key '${definition.jsonKey}'",
                    expectedType = expectedType,
                    actualElement = jsonElement,
                    context = context,
                ),
            )

        buildList {
            jsonArray.forEachIndexed { index, element ->
                val elementContext = context.atIndex(index)
                val primitive = element as? JsonPrimitive
                val value = primitive?.takeIf { it.isString }?.contentOrNull
                    ?: raise(
                        invalidTypeError(
                            subject = "list entry for key '${definition.jsonKey}'",
                            expectedType = "string",
                            actualElement = element,
                            context = elementContext,
                        ),
                    )
                add(value)
            }
        }
    }

    private fun parseStringListMapEither(
        definition: ConfigDefinition,
        jsonElement: JsonElement,
        context: DiagnosticContext,
    ): Either<ConfigError, Map<String, List<String>>> = either {
        val jsonObject = jsonElement as? JsonObject
            ?: raise(
                invalidTypeError(
                    subject = "value for key '${definition.jsonKey}'",
                    expectedType = expectedType(definition),
                    actualElement = jsonElement,
                    context = context,
                ),
            )

        buildMap {
            for ((mapKey, element) in jsonObject) {
                put(
                    mapKey,
                    parseStringListEither(
                        definition = definition,
                        jsonElement = element,
                        context = context.atKey(mapKey),
                        expectedType = "list of strings",
                    ).bind(),
                )
            }
        }
    }

    private fun parseStringMapEither(
        definition: ConfigDefinition,
        jsonElement: JsonElement,
        context: DiagnosticContext,
    ): Either<ConfigError, Map<String, String>> = either {
        val jsonObject = jsonElement as? JsonObject
            ?: raise(
                invalidTypeError(
                    subject = "value for key '${definition.jsonKey}'",
                    expectedType = expectedType(definition),
                    actualElement = jsonElement,
                    context = context,
                ),
            )

        buildMap {
            for ((mapKey, element) in jsonObject) {
                val primitive = element as? JsonPrimitive
                val value = primitive?.takeIf { it.isString }?.contentOrNull
                    ?: raise(
                        invalidTypeError(
                            subject = "map entry for key '${definition.jsonKey}'",
                            expectedType = "string",
                            actualElement = element,
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
        actualElement: JsonElement,
        context: DiagnosticContext,
    ): ConfigError.InvalidType = ConfigError.InvalidType(
        subject = subject,
        expectedType = expectedType,
        actualType = describeActualType(actualElement),
        context = context,
    )

    private fun closeKeyMatches(
        unknownKey: String,
        candidates: List<String>,
    ): List<String> {
        val normalizedUnknownKey = unknownKey.lowercase()
        return candidates
            .map { candidate ->
                candidate to levenshteinDistance(normalizedUnknownKey, candidate.lowercase())
            }
            .filter { (candidate, distance) ->
                distance <= suggestionThreshold(normalizedUnknownKey.length, candidate.length)
            }
            .sortedWith(compareBy<Pair<String, Int>>({ it.second }, { it.first }))
            .take(MAX_SUGGESTIONS)
            .map(Pair<String, Int>::first)
    }

    private fun suggestionThreshold(firstLength: Int, secondLength: Int): Int =
        maxOf(
            MIN_SUGGESTION_THRESHOLD,
            minOf(MAX_SUGGESTION_THRESHOLD, maxOf(firstLength, secondLength) / LENGTH_DIVISOR),
        )

    @Suppress("ReturnCount")
    private fun levenshteinDistance(first: String, second: String): Int {
        if (first == second) return 0
        if (first.isEmpty()) return second.length
        if (second.isEmpty()) return first.length

        var previous = IntArray(second.length + 1) { it }
        var current = IntArray(second.length + 1)

        for (firstIndex in first.indices) {
            current[0] = firstIndex + 1
            for (secondIndex in second.indices) {
                val substitutionCost = if (first[firstIndex] == second[secondIndex]) 0 else 1
                current[secondIndex + 1] = minOf(
                    current[secondIndex] + 1,
                    previous[secondIndex + 1] + 1,
                    previous[secondIndex] + substitutionCost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }

        return previous[second.length]
    }

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

    private fun describeActualType(jsonElement: JsonElement): String = when (jsonElement) {
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonPrimitive -> when {
            jsonElement.isString -> "string"
            jsonElement.content == NULL_LITERAL -> "null"
            jsonElement.booleanOrNull != null -> "boolean"
            jsonElement.intOrNull != null -> "int"
            jsonElement.longOrNull != null -> "long"
            jsonElement.doubleOrNull != null -> "double"
            else -> "number"
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
        internal const val FLAVORS_KEY = "flavors"
        internal const val RESOLVED_CONFIG_SOURCE_NAME = "resolved config"
        private const val DEFAULT_PARSE_SOURCE_NAME = "config"
        private const val DEFAULT_BASE_SOURCE_NAME = "default config"
        private const val DEFAULT_CUSTOM_SOURCE_NAME = "custom config"
        private const val MAX_SUGGESTIONS = 3
        private const val MIN_SUGGESTION_THRESHOLD = 1
        private const val MAX_SUGGESTION_THRESHOLD = 3
        private const val LENGTH_DIVISOR = 3
        private const val NULL_LITERAL = "null"
        internal val IDENTIFIER_SEGMENT = Regex("[A-Za-z_][A-Za-z0-9_]*")

        private fun StringBuilder.shouldInsertEnumSeparator(
            character: Char,
            previous: Char?,
        ): Boolean = character.isUpperCase() &&
            isNotEmpty() &&
            previous?.isLowerCase() == true &&
            last() != '_'
    }
}
