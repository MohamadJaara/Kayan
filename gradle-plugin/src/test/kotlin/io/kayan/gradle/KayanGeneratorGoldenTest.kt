package io.kayan.gradle

import io.kayan.ConfigDefinition
import io.kayan.ConfigSchema
import io.kayan.ConfigValue
import io.kayan.ConfigValueKind
import io.kayan.ResolvedFlavorConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class KayanGeneratorGoldenTest {
    private val bundleId = ConfigDefinition(
        jsonKey = "bundle_id",
        propertyName = "BUNDLE_ID",
        kind = ConfigValueKind.STRING,
        required = true,
    )
    private val brandName = ConfigDefinition(
        jsonKey = "brand_name",
        propertyName = "BRAND_NAME",
        kind = ConfigValueKind.STRING,
    )
    private val searchEnabled = ConfigDefinition(
        jsonKey = "feature_search_enabled",
        propertyName = "FEATURE_SEARCH_ENABLED",
        kind = ConfigValueKind.BOOLEAN,
    )
    private val maxWorkspaceCount = ConfigDefinition(
        jsonKey = "max_workspace_count",
        propertyName = "MAX_WORKSPACE_COUNT",
        kind = ConfigValueKind.INT,
    )
    private val maxCacheBytes = ConfigDefinition(
        jsonKey = "max_cache_bytes",
        propertyName = "MAX_CACHE_BYTES",
        kind = ConfigValueKind.LONG,
    )
    private val rolloutRatio = ConfigDefinition(
        jsonKey = "rollout_ratio",
        propertyName = "ROLLOUT_RATIO",
        kind = ConfigValueKind.DOUBLE,
    )
    private val supportLinks = ConfigDefinition(
        jsonKey = "support_links",
        propertyName = "SUPPORT_LINKS",
        kind = ConfigValueKind.STRING_LIST,
    )
    private val supportLabels = ConfigDefinition(
        jsonKey = "support_labels",
        propertyName = "SUPPORT_LABELS",
        kind = ConfigValueKind.STRING_MAP,
    )
    private val regionalSupportLinks = ConfigDefinition(
        jsonKey = "regional_support_links",
        propertyName = "REGIONAL_SUPPORT_LINKS",
        kind = ConfigValueKind.STRING_LIST_MAP,
    )
    private val releaseStage = ConfigDefinition(
        jsonKey = "release_stage",
        propertyName = "RELEASE_STAGE",
        kind = ConfigValueKind.ENUM,
        enumTypeName = "sample.ReleaseStage",
    )
    private val supportEmail = ConfigDefinition(
        jsonKey = "support_email",
        propertyName = "SUPPORT_EMAIL",
        kind = ConfigValueKind.STRING,
        nullable = true,
    )
    private val environment = ConfigDefinition(
        jsonKey = "environment",
        propertyName = "ENVIRONMENT",
        kind = ConfigValueKind.STRING,
        adapterClassName = "sample.EnvironmentAdapter",
    )
    private val schema = ConfigSchema(
        listOf(
            bundleId,
            brandName,
            searchEnabled,
            maxWorkspaceCount,
            maxCacheBytes,
            rolloutRatio,
            supportLinks,
            supportLabels,
            regionalSupportLinks,
            releaseStage,
            supportEmail,
            environment,
        )
    )

    private val resolvedFlavorConfig = ResolvedFlavorConfig(
        flavorName = "prod",
        values = mapOf(
            bundleId to ConfigValue.StringValue("com.example.prod"),
            brandName to ConfigValue.StringValue("Example"),
            searchEnabled to ConfigValue.BooleanValue(false),
            maxWorkspaceCount to ConfigValue.IntValue(42),
            maxCacheBytes to ConfigValue.LongValue(9876543210L),
            rolloutRatio to ConfigValue.DoubleValue(0.75),
            supportLinks to ConfigValue.StringListValue(
                listOf(
                    "https://example.com/help",
                    "https://example.com/docs",
                )
            ),
            supportLabels to ConfigValue.StringMapValue(
                mapOf(
                    "region" to "eu",
                    "channel" to "stable",
                )
            ),
            regionalSupportLinks to ConfigValue.StringListMapValue(
                mapOf(
                    "example.de" to listOf("sha-c"),
                    "example.com" to listOf("sha-a", "sha-b"),
                )
            ),
            releaseStage to ConfigValue.EnumValue("BETA"),
            supportEmail to ConfigValue.NullValue(ConfigValueKind.STRING),
            environment to ConfigValue.StringValue("prod"),
        ),
    )

    @Test
    fun generatedKotlinMatchesGoldenFile() {
        val actual = KayanConfigGenerator.generate(
            packageName = "sample.config",
            className = "KayanConfig",
            schema = schema,
            resolvedFlavorConfig = resolvedFlavorConfig,
            renderedCustomProperties = mapOf(
                environment to RenderedCustomProperty(
                    typeName = "sample.Environment",
                    expression = "sample.Environment.PROD",
                )
            ),
        )

        assertGoldenFile("/golden/KayanConfig.kt", actual)
    }

    @Test
    fun generatedJsonSchemaMatchesGoldenFile() {
        val actual = KayanSchemaExportGenerator.generateJsonSchema(schema)

        assertGoldenFile("/golden/kayan.schema.json", actual)
    }

    @Test
    fun generatedMarkdownSchemaMatchesGoldenFile() {
        val actual = KayanSchemaExportGenerator.generateMarkdown(
            schema = schema,
            generatedTypeName = "sample.config.KayanConfig",
        )

        assertGoldenFile("/golden/SCHEMA.md", actual)
    }

    private fun assertGoldenFile(resourcePath: String, actual: String) {
        val expected = checkNotNull(this::class.java.getResource(resourcePath)) {
            "Missing golden file at $resourcePath"
        }.readText()

        assertEquals(normalize(expected), normalize(actual), "Golden file mismatch for $resourcePath")
    }

    private fun normalize(value: String): String = value.replace("\r\n", "\n").trimEnd('\n')
}
