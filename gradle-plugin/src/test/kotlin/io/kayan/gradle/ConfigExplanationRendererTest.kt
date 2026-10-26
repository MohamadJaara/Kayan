package io.kayan.gradle

import io.kayan.ConfigDefinition
import io.kayan.ConfigLayer
import io.kayan.ConfigValue
import io.kayan.ConfigValueKind
import io.kayan.ConfigValueSource
import io.kayan.DiagnosticContext
import io.kayan.FlavorResolutionTrace
import io.kayan.ResolvedFlavorConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigExplanationRendererTest {
    @Test
    fun rendersEveryValueKindInDeclarationOrderWithCanonicalMapsAndEscapedStrings() {
        val values = linkedMapOf(
            "text" to ConfigValue.StringValue("quote\" slash\\ newline\n tab\t"),
            "boolean" to ConfigValue.BooleanValue(true),
            "int" to ConfigValue.IntValue(42),
            "long" to ConfigValue.LongValue(2147483648),
            "double" to ConfigValue.DoubleValue(1.25),
            "map" to ConfigValue.StringMapValue(linkedMapOf("z" to "last", "a" to "first\n")),
            "list" to ConfigValue.StringListValue(listOf("z", "a\"")),
            "list_map" to ConfigValue.StringListMapValue(linkedMapOf("z" to listOf("z"), "a" to listOf("a"))),
            "enum" to ConfigValue.EnumValue("PRODUCTION"),
            "null" to ConfigValue.NullValue(ConfigValueKind.STRING),
            "absent" to null,
        ).mapKeys { (key, value) ->
            ConfigDefinition(
                key,
                "_$key",
                value?.kind ?: ConfigValueKind.STRING,
                enumTypeName = if (value is ConfigValue.EnumValue) "sample.Mode" else null,
            )
        }
        val sources = values.filterValues { it != null }.mapValues { (definition, _) ->
            ConfigValueSource(
                DiagnosticContext("base.json").atFlavor("prod").atKey(definition.jsonKey),
                ConfigLayer.BASE_FLAVOR,
            )
        }
        val trace = FlavorResolutionTrace(ResolvedFlavorConfig("prod", values = values), sources)

        val output = renderConfigExplanation(trace)

        assertTrue(output.startsWith("Kayan configuration: flavor=\"prod\", target=<none>"))
        val valueLines = output.lines().filter { it.startsWith('"') }
        assertEquals(
            listOf(
                """"text" = "quote\" slash\\ newline\n tab\t"""",
                """"boolean" = true""",
                """"int" = 42""",
                """"long" = 2147483648""",
                """"double" = 1.25""",
                """"map" = {"a":"first\n","z":"last"}""",
                """"list" = ["z","a\""]""",
                """"list_map" = {"a":["a"],"z":["z"]}""",
                """"enum" = "PRODUCTION"""",
                """"null" = null""",
                """"absent" = <absent>""",
            ),
            valueLines,
        )
        assertTrue(output.endsWith("\"absent\" = <absent>\n  source: <none>"))
        assertTrue(output.contains("\"null\" = null\n  source: \"base.json\""))
        assertEquals(output, renderConfigExplanation(trace))
    }

    @Test
    fun escapesUnusualKeysPathsSourceNamesAndSelectorsWithoutExtraOutputLines() {
        val definition = ConfigDefinition("odd.key\n\"", "ODD_KEY", ConfigValueKind.STRING)
        val source = DiagnosticContext("file\n\".json").atFlavor("prod\n").atTarget("jvm\t")
            .atKey(definition.jsonKey)
        val trace = FlavorResolutionTrace(
            ResolvedFlavorConfig("prod\n", values = mapOf(definition to ConfigValue.StringValue("value"))),
            mapOf(definition to ConfigValueSource(source, ConfigLayer.CUSTOM_FLAVOR)),
        )

        val output = renderConfigExplanation(trace)

        assertEquals(5, output.lines().size)
        assertTrue(output.contains("flavor=\"prod\\n\""))
        assertTrue(output.contains("\"odd.key\\n\\\"\" = \"value\""))
        assertTrue(output.contains("source: \"file\\n\\\".json\""))
        assertTrue(output.contains("layer: custom flavor target overlay"))
    }
}
