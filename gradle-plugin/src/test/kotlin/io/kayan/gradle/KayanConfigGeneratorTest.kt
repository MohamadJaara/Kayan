package io.kayan.gradle

import com.squareup.kotlinpoet.TypeName
import io.kayan.ConfigDefinition
import io.kayan.ConfigSchema
import io.kayan.ConfigValue
import io.kayan.ConfigValueKind
import io.kayan.ResolvedFlavorConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KayanConfigGeneratorTest {
    @Test
    fun rendersRequiredScalarBuiltInsAsConstAndOtherPropertiesAsVal() {
        val bundleId = stringDefinition(
            jsonKey = "bundle_id",
            propertyName = "BUNDLE_ID",
            required = true,
        )
        val brandName = stringDefinition(
            jsonKey = "brand_name",
            propertyName = "BRAND_NAME",
        )
        val supportLinks = definition(
            jsonKey = "support_links",
            propertyName = "SUPPORT_LINKS",
            kind = ConfigValueKind.STRING_LIST,
            required = true,
        )
        val releaseStage = definition(
            jsonKey = "release_stage",
            propertyName = "RELEASE_STAGE",
            kind = ConfigValueKind.ENUM,
            enumTypeName = "sample.ReleaseStage",
            required = true,
        )
        val environment = stringDefinition(
            jsonKey = "environment",
            propertyName = "ENVIRONMENT",
            adapterClassName = "sample.EnvironmentAdapter",
        )

        val actual = generateSource(
            schema = ConfigSchema(
                listOf(bundleId, brandName, supportLinks, releaseStage, environment)
            ),
            resolvedValues = mapOf(
                bundleId to ConfigValue.StringValue("com.example.prod"),
                brandName to ConfigValue.StringValue("Example"),
                supportLinks to ConfigValue.StringListValue(listOf("https://example.com/help")),
                releaseStage to ConfigValue.EnumValue("BETA"),
                environment to ConfigValue.StringValue("prod"),
            ),
            renderedCustomProperties = mapOf(
                environment to RenderedCustomProperty(
                    typeName = bestGuessTypeName("sample.Environment"),
                    expression = "sample.Environment.PROD",
                )
            ),
        )

        assertEquals(
            "public const val BUNDLE_ID: String = \"com.example.prod\"",
            propertyLine(actual, "BUNDLE_ID"),
        )
        assertEquals(
            "public val BRAND_NAME: String = \"Example\"",
            propertyLine(actual, "BRAND_NAME"),
        )
        assertEquals(
            "public val SUPPORT_LINKS: List<String> = listOf(\"https://example.com/help\")",
            propertyLine(actual, "SUPPORT_LINKS"),
        )
        assertContains(propertyLine(actual, "RELEASE_STAGE"), "public val RELEASE_STAGE:")
        assertContains(propertyLine(actual, "RELEASE_STAGE"), "= sample.ReleaseStage.BETA")
        assertContains(propertyLine(actual, "ENVIRONMENT"), "public val ENVIRONMENT:")
        assertContains(propertyLine(actual, "ENVIRONMENT"), "= sample.Environment.PROD")
    }

    @Test
    fun rendersNullBuiltInAndCustomPropertiesAsNullableDeclarations() {
        val supportEmail = definition(
            jsonKey = "support_email",
            propertyName = "SUPPORT_EMAIL",
            kind = ConfigValueKind.STRING,
            nullable = true,
        )
        val environment = stringDefinition(
            jsonKey = "environment",
            propertyName = "ENVIRONMENT",
            adapterClassName = "sample.EnvironmentAdapter",
            nullable = true,
        )

        val actual = generateSource(
            schema = ConfigSchema(listOf(supportEmail, environment)),
            resolvedValues = mapOf(
                supportEmail to ConfigValue.NullValue(ConfigValueKind.STRING),
                environment to ConfigValue.NullValue(ConfigValueKind.STRING),
            ),
            renderedCustomProperties = mapOf(
                environment to RenderedCustomProperty(
                    typeName = bestGuessTypeName("sample.Environment"),
                    expression = null,
                )
            ),
        )

        assertEquals(
            "public val SUPPORT_EMAIL: String? = null",
            propertyLine(actual, "SUPPORT_EMAIL"),
        )
        assertMatchesDeclaration(
            actual = propertyLine(actual, "ENVIRONMENT"),
            expectedPattern = """public val ENVIRONMENT: (sample\.)?Environment\? = null""",
        )
    }

    @Test
    fun rendersParameterizedCustomPropertyTypes() {
        val environmentMatrix = stringDefinition(
            jsonKey = "environment_matrix",
            propertyName = "ENVIRONMENT_MATRIX",
            adapterClassName = "sample.EnvironmentMatrixAdapter",
        )

        val actual = generateSource(
            schema = ConfigSchema(listOf(environmentMatrix)),
            resolvedValues = mapOf(
                environmentMatrix to ConfigValue.StringValue("prod"),
            ),
            renderedCustomProperties = mapOf(
                environmentMatrix to RenderedCustomProperty(
                    typeName = KayanTypeNames.parameterized(
                        KayanTypeNames.bestGuess("sample.Box"),
                        bestGuessTypeName("sample.Environment"),
                    ),
                    expression = "sample.Box(sample.Environment.PROD)",
                )
            ),
        )

        assertContains(propertyLine(actual, "ENVIRONMENT_MATRIX"), "public val ENVIRONMENT_MATRIX:")
        assertContains(propertyLine(actual, "ENVIRONMENT_MATRIX"), "= sample.Box(sample.Environment.PROD)")
    }

    @Test
    fun preservesSchemaOrderAndSortsMapKeysForDeterministicOutput() {
        val supportLabels = definition(
            jsonKey = "support_labels",
            propertyName = "SUPPORT_LABELS",
            kind = ConfigValueKind.STRING_MAP,
        )
        val supportLinks = definition(
            jsonKey = "support_links",
            propertyName = "SUPPORT_LINKS",
            kind = ConfigValueKind.STRING_LIST,
        )
        val regionalSupportLinks = definition(
            jsonKey = "regional_support_links",
            propertyName = "REGIONAL_SUPPORT_LINKS",
            kind = ConfigValueKind.STRING_LIST_MAP,
        )

        val actual = generateSource(
            schema = ConfigSchema(listOf(supportLabels, supportLinks, regionalSupportLinks)),
            resolvedValues = linkedMapOf(
                supportLabels to ConfigValue.StringMapValue(
                    linkedMapOf(
                        "region" to "eu",
                        "channel" to "stable",
                    )
                ),
                supportLinks to ConfigValue.StringListValue(
                    listOf(
                        "https://example.com/help",
                        "https://example.com/docs",
                    )
                ),
                regionalSupportLinks to ConfigValue.StringListMapValue(
                    linkedMapOf(
                        "example.de" to listOf("sha-c"),
                        "example.com" to listOf("sha-a", "sha-b"),
                    )
                ),
            ),
        )

        assertEquals(
            "public val SUPPORT_LABELS: Map<String, String> = " +
                "mapOf(\"channel\" to \"stable\", \"region\" to \"eu\")",
            propertyLine(actual, "SUPPORT_LABELS"),
        )
        assertEquals(
            "public val REGIONAL_SUPPORT_LINKS: Map<String, List<String>> = " +
                "mapOf(\"example.com\" to listOf(\"sha-a\", \"sha-b\"), \"example.de\" to listOf(\"sha-c\"))",
            propertyLine(actual, "REGIONAL_SUPPORT_LINKS"),
        )
        assertTrue(
            actual.indexOf("public val SUPPORT_LABELS:") < actual.indexOf("public val SUPPORT_LINKS:"),
            "Expected SUPPORT_LABELS to be rendered before SUPPORT_LINKS.",
        )
        assertTrue(
            actual.indexOf("public val SUPPORT_LINKS:") < actual.indexOf("public val REGIONAL_SUPPORT_LINKS:"),
            "Expected SUPPORT_LINKS to be rendered before REGIONAL_SUPPORT_LINKS.",
        )
    }

    @Test
    fun rendersWholeNumberDoublesWithDecimalSuffix() {
        val rolloutRatio = definition(
            jsonKey = "rollout_ratio",
            propertyName = "ROLLOUT_RATIO",
            kind = ConfigValueKind.DOUBLE,
        )

        val actual = generateSource(
            schema = ConfigSchema(listOf(rolloutRatio)),
            resolvedValues = mapOf(rolloutRatio to ConfigValue.DoubleValue(1.0)),
        )

        assertEquals(
            "public val ROLLOUT_RATIO: Double = 1.0",
            propertyLine(actual, "ROLLOUT_RATIO"),
        )
    }

    @Test
    fun rendersExpectAndActualObjectsForTargetSpecificGeneration() {
        val bundleId = stringDefinition(
            jsonKey = "bundle_id",
            propertyName = "BUNDLE_ID",
            required = true,
        )
        val environment = stringDefinition(
            jsonKey = "environment",
            propertyName = "ENVIRONMENT",
            nullable = true,
            adapterClassName = "sample.EnvironmentAdapter",
        )
        val schema = ConfigSchema(listOf(bundleId, environment))

        val expectSource = KayanConfigGenerator.generate(
            packageName = "sample.config",
            className = "KayanConfig",
            schema = schema,
            declarationMode = KayanDeclarationMode.EXPECT,
            renderedCustomProperties = mapOf(
                environment to RenderedCustomProperty(
                    typeName = bestGuessTypeName("sample.Environment"),
                    expression = null,
                ),
            ),
        )
        val actualSource = KayanConfigGenerator.generate(
            packageName = "sample.config",
            className = "KayanConfig",
            schema = schema,
            declarationMode = KayanDeclarationMode.ACTUAL,
            resolvedFlavorConfig = ResolvedFlavorConfig(
                flavorName = "prod",
                targetName = "ios",
                values = mapOf(
                    bundleId to ConfigValue.StringValue("com.example.ios"),
                    environment to ConfigValue.NullValue(ConfigValueKind.STRING),
                ),
            ),
            renderedCustomProperties = mapOf(
                environment to RenderedCustomProperty(
                    typeName = bestGuessTypeName("sample.Environment"),
                    expression = null,
                ),
            ),
        )

        assertContains(expectSource, "public expect object KayanConfig")
        assertEquals("public val BUNDLE_ID: String", propertyLine(expectSource, "BUNDLE_ID"))
        assertMatchesDeclaration(
            actual = propertyLine(expectSource, "ENVIRONMENT"),
            expectedPattern = """public val ENVIRONMENT: (sample\.)?Environment\?""",
        )
        assertContains(actualSource, "public actual object KayanConfig")
        assertEquals(
            "public actual val BUNDLE_ID: String = \"com.example.ios\"",
            propertyLine(actualSource, "BUNDLE_ID"),
        )
        assertMatchesDeclaration(
            actual = propertyLine(actualSource, "ENVIRONMENT"),
            expectedPattern = """public actual val ENVIRONMENT: (sample\.)?Environment\? = null""",
        )
    }

    @Test
    fun requiresResolvedFlavorConfigForObjectAndActualGeneration() {
        val schema = ConfigSchema(
            listOf(
                stringDefinition(
                    jsonKey = "bundle_id",
                    propertyName = "BUNDLE_ID",
                    required = true,
                )
            )
        )

        val objectError = assertFailsWith<IllegalArgumentException> {
            KayanConfigGenerator.generate(
                packageName = "sample.config",
                className = "KayanConfig",
                schema = schema,
                declarationMode = KayanDeclarationMode.OBJECT,
            )
        }
        assertEquals(
            "resolvedFlavorConfig is required when declarationMode is OBJECT.",
            objectError.message,
        )

        val actualError = assertFailsWith<IllegalArgumentException> {
            KayanConfigGenerator.generate(
                packageName = "sample.config",
                className = "KayanConfig",
                schema = schema,
                declarationMode = KayanDeclarationMode.ACTUAL,
            )
        }
        assertEquals(
            "resolvedFlavorConfig is required when declarationMode is ACTUAL.",
            actualError.message,
        )
    }

    private fun generateSource(
        schema: ConfigSchema,
        resolvedValues: Map<ConfigDefinition, ConfigValue>,
        renderedCustomProperties: Map<ConfigDefinition, RenderedCustomProperty> = emptyMap(),
    ): String = KayanConfigGenerator.generate(
        packageName = "sample.config",
        className = "KayanConfig",
        schema = schema,
        resolvedFlavorConfig = ResolvedFlavorConfig(
            flavorName = "prod",
            values = resolvedValues,
        ),
        renderedCustomProperties = renderedCustomProperties,
    )

    private fun propertyLine(
        source: String,
        propertyName: String,
    ): String {
        val propertyPattern = Regex("""\b${Regex.escape(propertyName)}\s*:""")
        val lines = source.lineSequence().toList()
        val propertyIndex = lines.indexOfFirst { propertyPattern.containsMatchIn(it) }

        if (propertyIndex == -1) {
            error(
                "Expected generated source to contain a declaration for '$propertyName', " +
                    "but it was missing.\n$source",
            )
        }

        val declarationLines = mutableListOf<String>()
        for (index in propertyIndex until lines.size) {
            val line = lines[index]
            if (declarationLines.isNotEmpty() && (line.isBlank() || line.trim() == "}")) {
                break
            }
            declarationLines += line.trim()
        }

        return declarationLines.joinToString(separator = " ")
    }

    private fun assertMatchesDeclaration(
        actual: String,
        expectedPattern: String,
    ) {
        assertTrue(
            Regex(expectedPattern).matches(actual),
            "Expected <$actual> to match <$expectedPattern>.",
        )
    }

    private fun bestGuessTypeName(canonicalName: String): TypeName = KayanTypeNames.bestGuess(canonicalName)

    private fun stringDefinition(
        jsonKey: String,
        propertyName: String,
        required: Boolean = false,
        nullable: Boolean = false,
        adapterClassName: String? = null,
    ): ConfigDefinition = definition(
        jsonKey = jsonKey,
        propertyName = propertyName,
        kind = ConfigValueKind.STRING,
        required = required,
        nullable = nullable,
        adapterClassName = adapterClassName,
    )

    private fun definition(
        jsonKey: String,
        propertyName: String,
        kind: ConfigValueKind,
        required: Boolean = false,
        nullable: Boolean = false,
        enumTypeName: String? = null,
        adapterClassName: String? = null,
    ): ConfigDefinition = ConfigDefinition(
        jsonKey = jsonKey,
        propertyName = propertyName,
        kind = kind,
        required = required,
        nullable = nullable,
        enumTypeName = enumTypeName,
        adapterClassName = adapterClassName,
    )
}
