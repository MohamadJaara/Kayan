package io.kayan.gradle

import io.kayan.ConfigDefinition
import io.kayan.ConfigSchema
import io.kayan.ConfigValueKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object KayanSchemaExportGenerator {
    private const val flavorsKey: String = "flavors"
    private const val jsonSchemaSpecUrl: String = "https://json-schema.org/draft/2020-12/schema"

    private val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun generateJsonSchema(schema: ConfigSchema): String {
        val sectionProperties = buildJsonObject {
            schema.entries.forEach { definition ->
                put(definition.jsonKey, propertySchema(definition))
            }
        }

        val rootSchema = buildJsonObject {
            put("\$schema", JsonPrimitive(jsonSchemaSpecUrl))
            put("title", JsonPrimitive("Kayan config schema"))
            put(
                "description",
                JsonPrimitive(
                    "Top-level keys act as defaults for every flavor. Each flavor object accepts the same keys."
                ),
            )
            put("type", JsonPrimitive("object"))
            put("required", jsonArrayOf(flavorsKey))
            put("additionalProperties", JsonPrimitive(false))
            put("properties", rootProperties(sectionProperties))
            put(
                "\$defs",
                buildJsonObject {
                    put(
                        "configSection",
                        buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("additionalProperties", JsonPrimitive(false))
                            put("properties", sectionProperties)
                        },
                    )
                },
            )

            val requiredRules = schema.entries
                .filter(ConfigDefinition::required)
                .map(::requiredResolutionRule)
            if (requiredRules.isNotEmpty()) {
                put("allOf", JsonArray(requiredRules))
            }
        }

        return json.encodeToString(JsonElement.serializer(), rootSchema) + "\n"
    }

    fun generateMarkdown(
        schema: ConfigSchema,
        generatedTypeName: String?,
    ): String = buildString {
        appendLine("# Kayan Config Schema")
        appendLine()
        appendLine("Generated from `kayan { schema { ... } }`.")
        generatedTypeName?.let {
            appendLine("Generated Kotlin access point: `$it`.")
        }
        appendLine()
        appendLine("## File Shape")
        appendLine()
        appendLine("- The root object must contain `flavors`.")
        appendLine("- Top-level keys act as defaults for every flavor.")
        appendLine("- Every flavor object accepts the same keys as the top-level defaults section.")
        appendLine(
            "- Keys marked `required` must appear either at the top level or inside every " +
                "flavor to resolve for all variants.",
        )
        appendLine(
            "- Keys marked `preventOverride` can only be defined in the main config file. " +
                "Custom config files cannot set them.",
        )
        appendLine()
        appendLine("## Entries")
        appendLine()
        appendLine("| JSON key | Generated property | Raw JSON type | Required after resolution | Notes |")
        appendLine("| --- | --- | --- | --- | --- |")
        schema.entries.forEach { definition ->
            appendLine(
                buildString {
                    append("| `")
                    append(escapeMarkdownCell(definition.jsonKey))
                    append("` | `")
                    append(escapeMarkdownCell(definition.propertyName))
                    append("` | `")
                    append(rawTypeLabel(definition.kind))
                    append("` | ")
                    append(if (definition.required) "Yes" else "No")
                    append(" | ")
                    append(escapeMarkdownCell(notesFor(definition)))
                    append(" |")
                }
            )
        }
    }

    private fun rootProperties(sectionProperties: JsonObject): JsonObject = buildJsonObject {
        put(
            flavorsKey,
            buildJsonObject {
                put("type", JsonPrimitive("object"))
                put(
                    "description",
                    JsonPrimitive(
                        "Flavor-specific overrides. Each flavor follows the same schema as " +
                            "the top-level defaults section.",
                    ),
                )
                put(
                    "additionalProperties",
                    buildJsonObject {
                        put("\$ref", JsonPrimitive("#/\$defs/configSection"))
                    },
                )
            },
        )

        sectionProperties.forEach { (jsonKey, propertySchema) ->
            put(jsonKey, propertySchema)
        }
    }

    private fun propertySchema(definition: ConfigDefinition): JsonObject = buildJsonObject {
        val baseSchema = basePropertySchema(definition)
        val effectiveSchema = if (definition.nullable) {
            buildJsonObject {
                put(
                    "anyOf",
                    JsonArray(
                        listOf(
                            baseSchema,
                            buildJsonObject {
                                put("type", JsonPrimitive("null"))
                            },
                        ),
                    ),
                )
            }
        } else {
            baseSchema
        }

        effectiveSchema.forEach { (key, value) -> put(key, value) }

        put("title", JsonPrimitive(definition.propertyName))
        put("description", JsonPrimitive(notesFor(definition)))
        put("x-kayan-propertyName", JsonPrimitive(definition.propertyName))
        definition.enumTypeName?.let { put("x-kayan-enumType", JsonPrimitive(it)) }
        if (definition.required) {
            put("x-kayan-requiredAfterResolution", JsonPrimitive(true))
        }
        if (definition.nullable) {
            put("x-kayan-nullable", JsonPrimitive(true))
        }
        if (definition.preventOverride) {
            put("x-kayan-preventOverride", JsonPrimitive(true))
        }
        definition.adapterClassName?.let { adapterClassName ->
            put("x-kayan-adapter", JsonPrimitive(adapterClassName))
        }
    }

    private fun basePropertySchema(definition: ConfigDefinition): JsonObject = buildJsonObject {
        when (definition.kind) {
            ConfigValueKind.STRING -> put("type", JsonPrimitive("string"))
            ConfigValueKind.BOOLEAN -> put("type", JsonPrimitive("boolean"))
            ConfigValueKind.INT -> put("type", JsonPrimitive("integer"))
            ConfigValueKind.LONG -> put("type", JsonPrimitive("integer"))
            ConfigValueKind.DOUBLE -> put("type", JsonPrimitive("number"))
            ConfigValueKind.STRING_MAP -> {
                put("type", JsonPrimitive("object"))
                put(
                    "additionalProperties",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                    },
                )
            }

            ConfigValueKind.STRING_LIST -> {
                put("type", JsonPrimitive("array"))
                put(
                    "items",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                    },
                )
            }

            ConfigValueKind.STRING_LIST_MAP -> {
                put("type", JsonPrimitive("object"))
                put(
                    "additionalProperties",
                    buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put(
                            "items",
                            buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            },
                        )
                    },
                )
            }

            ConfigValueKind.ENUM -> {
                put("type", JsonPrimitive("string"))
            }
        }
    }

    private fun requiredResolutionRule(definition: ConfigDefinition): JsonObject = buildJsonObject {
        put(
            "description",
            JsonPrimitive(
                "Key '${definition.jsonKey}' is required after resolution. Define it at the " +
                    "top level or in every flavor.",
            ),
        )
        put(
            "anyOf",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("required", jsonArrayOf(definition.jsonKey))
                    },
                    buildJsonObject {
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    flavorsKey,
                                    buildJsonObject {
                                        put("type", JsonPrimitive("object"))
                                        put(
                                            "additionalProperties",
                                            buildJsonObject {
                                                put("required", jsonArrayOf(definition.jsonKey))
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                ),
            ),
        )
    }

    private fun notesFor(definition: ConfigDefinition): String {
        val notes = mutableListOf<String>()
        if (definition.required) {
            notes += "Required after resolution."
        }
        if (definition.nullable) {
            notes += "Allows explicit null values."
        }
        if (definition.preventOverride) {
            notes += "Cannot be set in custom config files; only the main config file may define it."
        }
        definition.enumTypeName?.let { enumTypeName ->
            notes += "Generates Kotlin enum values of `$enumTypeName` from normalized string input."
        }
        definition.adapterClassName?.let { adapterClassName ->
            notes += "Parsed from `${rawTypeLabel(definition.kind)}` with custom adapter " +
                "`$adapterClassName`."
        }
        if (notes.isEmpty()) {
            notes += "Built-in Kayan type."
        }
        return notes.joinToString(" ")
    }

    private fun rawTypeLabel(kind: ConfigValueKind): String = when (kind) {
        ConfigValueKind.STRING -> "string"
        ConfigValueKind.BOOLEAN -> "boolean"
        ConfigValueKind.INT -> "integer"
        ConfigValueKind.LONG -> "long"
        ConfigValueKind.DOUBLE -> "double"
        ConfigValueKind.STRING_MAP -> "map<string, string>"
        ConfigValueKind.STRING_LIST -> "array<string>"
        ConfigValueKind.STRING_LIST_MAP -> "map<string, array<string>>"
        ConfigValueKind.ENUM -> "string"
    }

    private fun jsonArrayOf(vararg values: String): JsonArray = JsonArray(values.map(::JsonPrimitive))

    private fun escapeMarkdownCell(value: String): String = value.replace("|", "\\|")
}
