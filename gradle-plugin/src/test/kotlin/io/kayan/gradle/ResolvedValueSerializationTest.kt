package io.kayan.gradle

import io.kayan.ConfigDefinition
import io.kayan.ConfigValue
import io.kayan.ConfigValueKind
import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResolvedValueSerializationTest {
    private val stringDefinition = ConfigDefinition(
        "brand_name",
        "BRAND_NAME",
        ConfigValueKind.STRING,
    )
    private val booleanDefinition = ConfigDefinition(
        "feature_search_enabled",
        "FEATURE_SEARCH_ENABLED",
        ConfigValueKind.BOOLEAN,
    )
    private val intDefinition = ConfigDefinition(
        "max_workspace_count",
        "MAX_WORKSPACE_COUNT",
        ConfigValueKind.INT,
    )
    private val longDefinition = ConfigDefinition(
        "max_cache_bytes",
        "MAX_CACHE_BYTES",
        ConfigValueKind.LONG,
    )
    private val doubleDefinition = ConfigDefinition(
        "rollout_ratio",
        "ROLLOUT_RATIO",
        ConfigValueKind.DOUBLE,
    )
    private val stringMapDefinition = ConfigDefinition(
        "support_labels",
        "SUPPORT_LABELS",
        ConfigValueKind.STRING_MAP,
    )
    private val stringListDefinition = ConfigDefinition(
        "support_links",
        "SUPPORT_LINKS",
        ConfigValueKind.STRING_LIST,
    )
    private val stringListMapDefinition = ConfigDefinition(
        "regional_support_links",
        "REGIONAL_SUPPORT_LINKS",
        ConfigValueKind.STRING_LIST_MAP,
    )
    private val enumDefinition = ConfigDefinition(
        "release_stage",
        "RELEASE_STAGE",
        ConfigValueKind.ENUM,
        enumTypeName = "sample.ReleaseStage",
    )
    private val nullableStringDefinition = ConfigDefinition(
        "support_email",
        "SUPPORT_EMAIL",
        ConfigValueKind.STRING,
        nullable = true,
    )

    @Test
    fun roundTripsAllSupportedResolvedValueKinds() {
        assertRoundTrip(
            definition = stringDefinition,
            value = ConfigValue.StringValue("Example"),
            expected = ResolvedBuildValue("brand_name", ConfigValueKind.STRING, "Example"),
        )
        assertRoundTrip(
            definition = booleanDefinition,
            value = ConfigValue.BooleanValue(true),
            expected = ResolvedBuildValue("feature_search_enabled", ConfigValueKind.BOOLEAN, true),
        )
        assertRoundTrip(
            definition = intDefinition,
            value = ConfigValue.IntValue(42),
            expected = ResolvedBuildValue("max_workspace_count", ConfigValueKind.INT, 42),
        )
        assertRoundTrip(
            definition = longDefinition,
            value = ConfigValue.LongValue(9876543210L),
            expected = ResolvedBuildValue("max_cache_bytes", ConfigValueKind.LONG, 9876543210L),
        )
        assertRoundTrip(
            definition = doubleDefinition,
            value = ConfigValue.DoubleValue(0.75),
            expected = ResolvedBuildValue("rollout_ratio", ConfigValueKind.DOUBLE, 0.75),
        )
        assertRoundTrip(
            definition = stringMapDefinition,
            value = ConfigValue.StringMapValue(
                mapOf(
                    "region" to "eu",
                    "channel" to "stable",
                ),
            ),
            expected = ResolvedBuildValue(
                "support_labels",
                ConfigValueKind.STRING_MAP,
                mapOf(
                    "channel" to "stable",
                    "region" to "eu",
                ),
            ),
        )
        assertRoundTrip(
            definition = stringListDefinition,
            value = ConfigValue.StringListValue(
                listOf(
                    "https://example.com/help",
                    "https://example.com/docs",
                ),
            ),
            expected = ResolvedBuildValue(
                "support_links",
                ConfigValueKind.STRING_LIST,
                listOf("https://example.com/help", "https://example.com/docs"),
            ),
        )
        assertRoundTrip(
            definition = stringListMapDefinition,
            value = ConfigValue.StringListMapValue(
                mapOf(
                    "example.com" to listOf("sha-a", "sha-b"),
                    "example.de" to listOf("sha-c"),
                ),
            ),
            expected = ResolvedBuildValue(
                "regional_support_links",
                ConfigValueKind.STRING_LIST_MAP,
                mapOf(
                    "example.com" to listOf("sha-a", "sha-b"),
                    "example.de" to listOf("sha-c"),
                ),
            ),
        )
        assertRoundTrip(
            definition = enumDefinition,
            value = ConfigValue.EnumValue("BETA"),
            expected = ResolvedBuildValue("release_stage", ConfigValueKind.ENUM, "BETA"),
        )
    }

    @Test
    fun roundTripsNullValues() {
        assertRoundTrip(
            definition = nullableStringDefinition,
            value = ConfigValue.NullValue(ConfigValueKind.STRING),
            expected = ResolvedBuildValue(
                "support_email",
                ConfigValueKind.STRING,
                null,
            ),
        )
    }

    @Test
    fun roundTripsEmptyCollections() {
        assertRoundTrip(
            definition = stringMapDefinition,
            value = ConfigValue.StringMapValue(emptyMap()),
            expected = ResolvedBuildValue(
                "support_labels",
                ConfigValueKind.STRING_MAP,
                emptyMap<String, String>(),
            ),
        )
        assertRoundTrip(
            definition = stringListDefinition,
            value = ConfigValue.StringListValue(emptyList()),
            expected = ResolvedBuildValue(
                "support_links",
                ConfigValueKind.STRING_LIST,
                emptyList<String>(),
            ),
        )
        assertRoundTrip(
            definition = stringListMapDefinition,
            value = ConfigValue.StringListMapValue(emptyMap()),
            expected = ResolvedBuildValue(
                "regional_support_links",
                ConfigValueKind.STRING_LIST_MAP,
                emptyMap<String, List<String>>(),
            ),
        )
    }

    @Test
    fun invalidSerializedRootThrowsTypedGradleError() {
        val error = assertFailsWith<GradleException> {
            deserializeResolvedBuildValue("brand_name", "[]")
        }

        assertMessageContains(error, "Serialized resolved values must be a JSON object.")
    }

    @Test
    fun invalidEntryEncodingThrowsTypedGradleError() {
        val error = assertFailsWith<GradleException> {
            deserializeResolvedBuildValue(
                "support_links",
                """
                    {
                      "kind": "STRING_LIST",
                      "value": ["https://example.com/help", 7]
                    }
                """.trimIndent(),
            )
        }

        assertMessageContains(
            error,
            "Resolved value 'support_links' entry at index 1 must be a string.",
        )
    }

    private fun assertRoundTrip(
        definition: ConfigDefinition,
        value: ConfigValue?,
        expected: ResolvedBuildValue,
    ) {
        val actual = deserializeResolvedBuildValue(
            jsonKey = definition.jsonKey,
            serialized = serializeResolvedBuildValue(definition, value),
        )

        assertEquals(expected, actual)
    }
}
