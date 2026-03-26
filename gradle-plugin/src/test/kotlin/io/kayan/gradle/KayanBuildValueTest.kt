package io.kayan.gradle

import io.kayan.ConfigValueKind
import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalKayanGradleApi::class)
class KayanBuildValueTest {
    @Test
    fun typedAccessorsReturnMatchingValues() {
        assertEquals("Example", buildValue("brand_name", ConfigValueKind.STRING, "Example").asString())
        assertEquals("BETA", buildValue("release_stage", ConfigValueKind.ENUM, "BETA").asString())
        assertEquals("BETA", buildValue("release_stage", ConfigValueKind.ENUM, "BETA").asEnumName())
        assertEquals(true, buildValue("feature_search_enabled", ConfigValueKind.BOOLEAN, true).asBoolean())
        assertEquals(42, buildValue("max_workspace_count", ConfigValueKind.INT, 42).asInt())
        assertEquals(9876543210L, buildValue("max_cache_bytes", ConfigValueKind.LONG, 9876543210L).asLong())
        assertEquals(0.75, buildValue("rollout_ratio", ConfigValueKind.DOUBLE, 0.75).asDouble())
        assertEquals(
            listOf("https://example.com/help"),
            buildValue(
                "support_links",
                ConfigValueKind.STRING_LIST,
                listOf("https://example.com/help"),
            ).asStringList(),
        )
        assertEquals(
            mapOf("channel" to "stable"),
            buildValue(
                "support_labels",
                ConfigValueKind.STRING_MAP,
                mapOf("channel" to "stable"),
            ).asStringMap(),
        )
        assertEquals(
            mapOf("example.com" to listOf("sha-a")),
            buildValue(
                "regional_support_links",
                ConfigValueKind.STRING_LIST_MAP,
                mapOf("example.com" to listOf("sha-a")),
            ).asStringListMap(),
        )
    }

    @Test
    fun providerAccessorsReturnMatchingValues() {
        assertEquals("Example", buildValue("brand_name", ConfigValueKind.STRING, "Example").asStringProvider().get())
        assertEquals(
            true,
            buildValue("feature_search_enabled", ConfigValueKind.BOOLEAN, true).asBooleanProvider().get(),
        )
        assertEquals(42, buildValue("max_workspace_count", ConfigValueKind.INT, 42).asIntProvider().get())
        assertEquals(
            9876543210L,
            buildValue("max_cache_bytes", ConfigValueKind.LONG, 9876543210L).asLongProvider().get(),
        )
        assertEquals(0.75, buildValue("rollout_ratio", ConfigValueKind.DOUBLE, 0.75).asDoubleProvider().get())
        assertEquals(
            listOf("https://example.com/help"),
            buildValue(
                "support_links",
                ConfigValueKind.STRING_LIST,
                listOf("https://example.com/help"),
            ).asStringListProvider().get(),
        )
        assertEquals(
            mapOf("channel" to "stable"),
            buildValue(
                "support_labels",
                ConfigValueKind.STRING_MAP,
                mapOf("channel" to "stable"),
            ).asStringMapProvider().get(),
        )
        assertEquals(
            mapOf("example.com" to listOf("sha-a")),
            buildValue(
                "regional_support_links",
                ConfigValueKind.STRING_LIST_MAP,
                mapOf("example.com" to listOf("sha-a")),
            ).asStringListMapProvider().get(),
        )
        assertEquals("BETA", buildValue("release_stage", ConfigValueKind.ENUM, "BETA").asEnumNameProvider().get())
    }

    @Test
    fun typeMismatchThrowsDescriptiveError() {
        val error = assertFailsWith<GradleException> {
            buildValue("brand_name", ConfigValueKind.STRING, "Example").asBoolean()
        }

        assertMessageContains(error, "Key 'brand_name' is STRING, cannot access as Boolean")
    }

    @Test
    fun nullValueWithNonNullableAccessorThrowsHelpfulError() {
        val error = assertFailsWith<GradleException> {
            buildValue("support_email", ConfigValueKind.STRING, null).asString()
        }

        assertMessageContains(error, "Key 'support_email' is null; use asStringOrNull() instead")
    }

    @Test
    fun missingSchemaKeyThrowsWithSuggestions() {
        val extension = createExtension()

        val error = assertFailsWith<GradleException> {
            extension.buildValue("brand_nam")
        }

        assertMessageContains(
            error,
            "Key 'brand_nam' is not defined in the Kayan schema.",
            "Did you mean 'brand_name'?",
        )
    }

    @Test
    fun buildValueResolvesConfiguredExtensionValue() {
        val extension = createExtension()

        val value = extension.buildValue("brand_name").asString()

        assertEquals("Example", value)
    }

    @Test
    fun nullableAccessorReturnsNullWhenValueIsNull() {
        assertNull(buildValue("support_email", ConfigValueKind.STRING, null).asStringOrNull())
        assertNull(buildValue("feature_search_enabled", ConfigValueKind.BOOLEAN, null).asBooleanOrNull())
        assertNull(buildValue("max_workspace_count", ConfigValueKind.INT, null).asIntOrNull())
        assertNull(buildValue("max_cache_bytes", ConfigValueKind.LONG, null).asLongOrNull())
        assertNull(buildValue("rollout_ratio", ConfigValueKind.DOUBLE, null).asDoubleOrNull())
        assertNull(buildValue("support_links", ConfigValueKind.STRING_LIST, null).asStringListOrNull())
        assertNull(buildValue("support_labels", ConfigValueKind.STRING_MAP, null).asStringMapOrNull())
        assertNull(buildValue("regional_support_links", ConfigValueKind.STRING_LIST_MAP, null).asStringListMapOrNull())
        assertNull(buildValue("release_stage", ConfigValueKind.ENUM, null).asEnumNameOrNull())
    }

    @Test
    fun nullableAccessorReturnsValueWhenPresent() {
        assertEquals("Example", buildValue("brand_name", ConfigValueKind.STRING, "Example").asStringOrNull())
        assertEquals(
            true,
            buildValue("feature_search_enabled", ConfigValueKind.BOOLEAN, true).asBooleanOrNull(),
        )
        assertEquals(42, buildValue("max_workspace_count", ConfigValueKind.INT, 42).asIntOrNull())
        assertEquals(9876543210L, buildValue("max_cache_bytes", ConfigValueKind.LONG, 9876543210L).asLongOrNull())
        assertEquals(0.75, buildValue("rollout_ratio", ConfigValueKind.DOUBLE, 0.75).asDoubleOrNull())
        assertEquals(
            listOf("https://example.com/help"),
            buildValue(
                "support_links",
                ConfigValueKind.STRING_LIST,
                listOf("https://example.com/help"),
            ).asStringListOrNull(),
        )
        assertEquals(
            mapOf("channel" to "stable"),
            buildValue(
                "support_labels",
                ConfigValueKind.STRING_MAP,
                mapOf("channel" to "stable"),
            ).asStringMapOrNull(),
        )
        assertEquals(
            mapOf("example.com" to listOf("sha-a")),
            buildValue(
                "regional_support_links",
                ConfigValueKind.STRING_LIST_MAP,
                mapOf("example.com" to listOf("sha-a")),
            ).asStringListMapOrNull(),
        )
        assertEquals("BETA", buildValue("release_stage", ConfigValueKind.ENUM, "BETA").asEnumNameOrNull())
    }

    @Test
    fun nullableAccessorDoesNotSilentlyHideDecodedTypeMismatch() {
        val error = assertFailsWith<GradleException> {
            buildValue("feature_search_enabled", ConfigValueKind.BOOLEAN, "true").asBooleanOrNull()
        }

        assertMessageContains(
            error,
            "Key 'feature_search_enabled' was decoded as kotlin.String, cannot access as Boolean",
        )
    }

    private fun buildValue(
        jsonKey: String,
        kind: ConfigValueKind,
        rawValue: Any?,
    ): KayanBuildValue {
        val project = ProjectBuilder.builder().build()

        return KayanBuildValue(
            valueProvider = project.providers.provider {
                ResolvedBuildValue(jsonKey, kind, rawValue)
            },
        )
    }

    private fun createExtension(): KayanExtension {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(KayanConfigPlugin::class.java)

        val extension = project.extensions.getByType(KayanExtension::class.java)
        val tempDir = createTempDirectory(prefix = "kayan-build-value-test").toFile()
        val baseFile = File(tempDir, "default.json").apply {
            writeText(
                """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod",
                          "brand_name": "Example"
                        }
                      }
                    }
                """.trimIndent(),
            )
        }

        extension.packageName.set("sample.config")
        extension.flavor.set("prod")
        extension.baseConfigFile.set(baseFile)
        extension.schema {
            string("bundle_id", "BUNDLE_ID", required = true)
            string("brand_name", "BRAND_NAME")
        }

        return extension
    }
}
