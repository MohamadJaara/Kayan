package io.kayan.gradle

import io.kayan.ConfigDefinition
import io.kayan.ConfigValue
import io.kayan.ConfigValueKind
import io.kayan.ResolvedFlavorConfig
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
        val resolvedFlavor = ResolvedFlavorConfig(
            flavorName = "prod",
            values = mapOf(
                stringDefinition to ConfigValue.StringValue("Example"),
                booleanDefinition to ConfigValue.BooleanValue(true),
                intDefinition to ConfigValue.IntValue(42),
                longDefinition to ConfigValue.LongValue(9876543210L),
                doubleDefinition to ConfigValue.DoubleValue(0.75),
                stringMapDefinition to ConfigValue.StringMapValue(
                    mapOf(
                        "region" to "eu",
                        "channel" to "stable",
                    ),
                ),
                stringListDefinition to ConfigValue.StringListValue(
                    listOf(
                        "https://example.com/help",
                        "https://example.com/docs",
                    ),
                ),
                stringListMapDefinition to ConfigValue.StringListMapValue(
                    mapOf(
                        "example.com" to listOf("sha-a", "sha-b"),
                        "example.de" to listOf("sha-c"),
                    ),
                ),
                enumDefinition to ConfigValue.EnumValue("BETA"),
            ),
        )

        val actual = deserializeResolvedValues(serializeResolvedValues(resolvedFlavor))

        assertEquals(
            mapOf(
                "brand_name" to ResolvedBuildValue(
                    "brand_name",
                    ConfigValueKind.STRING,
                    "Example",
                ),
                "feature_search_enabled" to ResolvedBuildValue(
                    "feature_search_enabled",
                    ConfigValueKind.BOOLEAN,
                    true,
                ),
                "max_workspace_count" to ResolvedBuildValue(
                    "max_workspace_count",
                    ConfigValueKind.INT,
                    42,
                ),
                "max_cache_bytes" to ResolvedBuildValue(
                    "max_cache_bytes",
                    ConfigValueKind.LONG,
                    9876543210L,
                ),
                "rollout_ratio" to ResolvedBuildValue(
                    "rollout_ratio",
                    ConfigValueKind.DOUBLE,
                    0.75,
                ),
                "support_labels" to ResolvedBuildValue(
                    "support_labels",
                    ConfigValueKind.STRING_MAP,
                    mapOf(
                        "channel" to "stable",
                        "region" to "eu",
                    ),
                ),
                "support_links" to ResolvedBuildValue(
                    "support_links",
                    ConfigValueKind.STRING_LIST,
                    listOf("https://example.com/help", "https://example.com/docs"),
                ),
                "regional_support_links" to ResolvedBuildValue(
                    "regional_support_links",
                    ConfigValueKind.STRING_LIST_MAP,
                    mapOf(
                        "example.com" to listOf("sha-a", "sha-b"),
                        "example.de" to listOf("sha-c"),
                    ),
                ),
                "release_stage" to ResolvedBuildValue(
                    "release_stage",
                    ConfigValueKind.ENUM,
                    "BETA",
                ),
            ),
            actual,
        )
    }

    @Test
    fun roundTripsNullValues() {
        val resolvedFlavor = ResolvedFlavorConfig(
            flavorName = "prod",
            values = mapOf(
                nullableStringDefinition to ConfigValue.NullValue(ConfigValueKind.STRING),
            ),
        )

        val actual = deserializeResolvedValues(serializeResolvedValues(resolvedFlavor))

        assertEquals(
            mapOf(
                "support_email" to ResolvedBuildValue(
                    "support_email",
                    ConfigValueKind.STRING,
                    null,
                ),
            ),
            actual,
        )
    }

    @Test
    fun roundTripsEmptyCollections() {
        val resolvedFlavor = ResolvedFlavorConfig(
            flavorName = "prod",
            values = mapOf(
                stringMapDefinition to ConfigValue.StringMapValue(emptyMap()),
                stringListDefinition to ConfigValue.StringListValue(emptyList()),
                stringListMapDefinition to ConfigValue.StringListMapValue(emptyMap()),
            ),
        )

        val actual = deserializeResolvedValues(serializeResolvedValues(resolvedFlavor))

        assertEquals(
            mapOf(
                "support_labels" to ResolvedBuildValue(
                    "support_labels",
                    ConfigValueKind.STRING_MAP,
                    emptyMap<String, String>(),
                ),
                "support_links" to ResolvedBuildValue(
                    "support_links",
                    ConfigValueKind.STRING_LIST,
                    emptyList<String>(),
                ),
                "regional_support_links" to ResolvedBuildValue(
                    "regional_support_links",
                    ConfigValueKind.STRING_LIST_MAP,
                    emptyMap<String, List<String>>(),
                ),
            ),
            actual,
        )
    }

    @Test
    fun invalidSerializedRootThrowsTypedGradleError() {
        val error = assertFailsWith<GradleException> {
            deserializeResolvedValues("[]")
        }

        assertMessageContains(error, "Serialized resolved values must be a JSON object.")
    }

    @Test
    fun invalidEntryEncodingThrowsTypedGradleError() {
        val error = assertFailsWith<GradleException> {
            deserializeResolvedValues(
                """
                    {
                      "support_links": {
                        "kind": "STRING_LIST",
                        "value": ["https://example.com/help", 7]
                      }
                    }
                """.trimIndent(),
            )
        }

        assertMessageContains(
            error,
            "Resolved value 'support_links' entry at index 1 must be a string.",
        )
    }
}
