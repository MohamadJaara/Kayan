@file:OptIn(ExperimentalKayanApi::class)

package io.kayan

import arrow.core.Either
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class DefaultConfigResolverTraceTest {
    private val resolver = DefaultConfigResolver()
    private val entries = (0..7).map { ConfigDefinition("key$it", "KEY$it", ConfigValueKind.STRING) }
    private val schema = ConfigSchema(entries)
    private val base = document(root = 7, rootTarget = 6, flavor = 5, flavorTarget = 4)
    private val custom = document(root = 3, rootTarget = 2, flavor = 1, flavorTarget = 0)

    @Test
    fun tracesEveryWinningLayerAgainstAllLowerPriorityCompetitors() {
        val trace = resolve(targetName = "jvm").flavors.getValue("prod")
        val contexts = listOf(
            DiagnosticContext("custom.json").atFlavor("prod").atTarget("jvm"),
            DiagnosticContext("custom.json").atFlavor("prod"),
            DiagnosticContext("custom.json").atTarget("jvm"),
            DiagnosticContext("custom.json"),
            DiagnosticContext("base.json").atFlavor("prod").atTarget("jvm"),
            DiagnosticContext("base.json").atFlavor("prod"),
            DiagnosticContext("base.json").atTarget("jvm"),
            DiagnosticContext("base.json"),
        )
        val layers = listOf(
            ConfigLayer.CUSTOM_FLAVOR,
            ConfigLayer.CUSTOM_FLAVOR,
            ConfigLayer.CUSTOM_ROOT,
            ConfigLayer.CUSTOM_ROOT,
            ConfigLayer.BASE_FLAVOR,
            ConfigLayer.BASE_FLAVOR,
            ConfigLayer.BASE_ROOT,
            ConfigLayer.BASE_ROOT,
        )

        entries.forEachIndexed { index, definition ->
            assertEquals(ConfigValue.StringValue("layer$index"), trace.resolved[definition])
            assertEquals(
                ConfigValueSource(contexts[index].atKey(definition.jsonKey), layers[index]),
                trace.sources[definition],
            )
        }
        assertEquals(
            assertIs<Either.Right<ResolvedConfigsByFlavor>>(
                resolver.resolveEither(base, schema, custom, "base.json", "custom.json", "jvm"),
            ).value,
            resolve("jvm").resolved,
        )
    }

    @Test
    fun omittingTargetSkipsOverlaysAndCustomRootStillBeatsBaseFlavor() {
        val trace = resolve(targetName = null).flavors.getValue("prod")

        listOf(1, 1, 3, 3, 5, 5, 7, 7).forEachIndexed { index, winningLayer ->
            assertEquals(ConfigValue.StringValue("layer$winningLayer"), trace.resolved[entries[index]])
            assertNull(trace.sources.getValue(entries[index]).context.targetName)
        }
    }

    @Test
    fun targetWithoutAnOverlayUsesTheExistingFallbackBehavior() {
        val selected = resolve(targetName = "other").flavors.getValue("prod")
        val common = resolve(targetName = null).flavors.getValue("prod")

        assertEquals(common.resolved.values, selected.resolved.values)
        assertEquals(common.sources, selected.sources)
        assertEquals("other", selected.resolved.targetName)
    }

    @Test
    fun explicitNullWinsAndRetainsProvenanceWhileAbsenceHasNoSource() {
        val nullable = ConfigDefinition("nullable", "NULLABLE", ConfigValueKind.STRING, nullable = true)
        val absent = ConfigDefinition("absent", "ABSENT", ConfigValueKind.STRING)
        val result = resolver.resolveWithTraceEither(
            defaultConfigJson = """{"nullable":"lower", "flavors":{"prod":{}}}""",
            schema = ConfigSchema(listOf(nullable, absent)),
            customConfigJson = """{"flavors":{"prod":{"nullable":null}}}""",
            defaultConfigSourceName = "base.json",
            customConfigSourceName = "custom.json",
        )
        val trace = assertIs<Either.Right<ConfigResolutionTrace>>(result).value.flavors.getValue("prod")

        assertEquals(ConfigValue.NullValue(ConfigValueKind.STRING), trace.resolved[nullable])
        assertEquals("$.flavors.prod.nullable", trace.sources.getValue(nullable).context.path)
        assertNull(trace.resolved[absent])
        assertFalse(trace.sources.containsKey(absent))
    }

    @Test
    fun tracePreservesTypedValidationAndPreventOverrideFailures() {
        val protected = ConfigSchema(
            listOf(ConfigDefinition("key0", "KEY0", ConfigValueKind.STRING, preventOverride = true)),
        )
        val protectedResult = resolver.resolveWithTraceEither(
            defaultConfigJson = """{"flavors":{"prod":{}},"targets":{"jvm":{}}}""",
            schema = protected,
            customConfigJson = """{"flavors":{"prod":{"targets":{"jvm":{"key0":"blocked"}}}}}""",
            defaultConfigSourceName = "base.json",
            customConfigSourceName = "custom.json",
        )
        val error = assertIs<ConfigError.PreventedCustomOverride>(
            assertIs<Either.Left<ConfigError>>(protectedResult).value,
        )
        assertEquals("custom.json", error.customContext.sourceName)
        assertEquals("$.flavors.prod.targets.jvm.key0", error.customContext.path)

        val invalidType = resolver.resolveWithTraceEither(
            """{"key0":false,"flavors":{"prod":{}}}""",
            schema,
            null,
            "base.json",
        )
        assertIs<ConfigError.InvalidType>(assertIs<Either.Left<ConfigError>>(invalidType).value)
        val invalidSyntax = resolver.resolveWithTraceEither("{", schema, null, "broken.json")
        assertIs<ConfigError.InvalidConfigSyntax>(assertIs<Either.Left<ConfigError>>(invalidSyntax).value)
    }

    private fun resolve(targetName: String?): ConfigResolutionTrace = assertIs<Either.Right<ConfigResolutionTrace>>(
        resolver.resolveWithTraceEither(base, schema, custom, "base.json", "custom.json", targetName),
    ).value

    private fun document(root: Int, rootTarget: Int, flavor: Int, flavorTarget: Int): String = JsonObject(
        section(root).toMutableMap().apply {
            put("targets", JsonObject(mapOf("jvm" to section(rootTarget))))
            put(
                "flavors",
                JsonObject(
                    mapOf(
                        "prod" to JsonObject(
                            section(flavor) + ("targets" to JsonObject(mapOf("jvm" to section(flavorTarget)))),
                        ),
                    ),
                ),
            )
        },
    ).toString()

    private fun section(index: Int): JsonObject = JsonObject(
        (0..index).associate { "key$it" to JsonPrimitive("layer$index") },
    )
}
