package io.kayan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultConfigResolverTest {
    private val resolver = DefaultConfigResolver()
    private val emptyValue = ConfigDefinition(
        jsonKey = "empty_value",
        propertyName = "EMPTY_VALUE",
        kind = ConfigValueKind.STRING,
    )
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
    private val apiSecret = ConfigDefinition(
        jsonKey = "api_secret",
        propertyName = "API_SECRET",
        kind = ConfigValueKind.STRING,
        preventOverride = true,
    )
    private val onboardingEnabled = ConfigDefinition(
        jsonKey = "onboarding_enabled",
        propertyName = "ONBOARDING_ENABLED",
        kind = ConfigValueKind.BOOLEAN,
    )
    private val maxWorkspaceCount = ConfigDefinition(
        jsonKey = "max_workspace_count",
        propertyName = "MAX_WORKSPACE_COUNT",
        kind = ConfigValueKind.INT,
    )
    private val quotedNumericString = ConfigDefinition(
        jsonKey = "quoted_numeric_string",
        propertyName = "QUOTED_NUMERIC_STRING",
        kind = ConfigValueKind.STRING,
    )
    private val supportLinks = ConfigDefinition(
        jsonKey = "support_links",
        propertyName = "SUPPORT_LINKS",
        kind = ConfigValueKind.STRING_LIST,
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
    private val plainString = ConfigDefinition(
        jsonKey = "plain_string",
        propertyName = "PLAIN_STRING",
        kind = ConfigValueKind.STRING,
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
    private val schema = ConfigSchema(
        listOf(
            emptyValue,
            bundleId,
            brandName,
            searchEnabled,
            apiSecret,
            onboardingEnabled,
            maxWorkspaceCount,
            quotedNumericString,
            maxCacheBytes,
            rolloutRatio,
            plainString,
            supportLinks,
            supportLabels,
            regionalSupportLinks,
            releaseStage,
            supportEmail,
        )
    )

    @Test
    fun normalizesTopLevelValuesIntoFlavorResolution() {
        val resolved = resolver.resolve(
            defaultConfigJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "onboarding_enabled": true
                }
            """.trimIndent(),
            schema = schema,
        )

        assertEquals(
            ConfigValue.BooleanValue(true),
            resolved.flavors.getValue("prod")[onboardingEnabled]
        )
    }

    @Test
    fun preservesQuotedJsonValuesAsStrings() {
        val resolved = resolver.resolve(
            defaultConfigJson = """
                {
                  "flavors": {
                    "dev": {
                      "bundle_id": "com.example.dev",
                      "empty_value": "",
                      "quoted_numeric_string": "123",
                      "plain_string": "abc",
                      "max_workspace_count": 123
                    }
                  }
                }
            """.trimIndent(),
            schema = schema,
        )

        val flavor = resolved.flavors.getValue("dev")
        assertEquals(ConfigValue.StringValue(""), flavor[emptyValue])
        assertEquals(ConfigValue.StringValue("123"), flavor[quotedNumericString])
        assertEquals(ConfigValue.StringValue("abc"), flavor[plainString])
        assertEquals(ConfigValue.IntValue(123), flavor[maxWorkspaceCount])
    }

    @Test
    fun parsePreservesTopLevelAndFlavorTargetSections() {
        val parsed = resolver.parse(
            configJson = """
                {
                  "brand_name": "Base",
                  "targets": {
                    "ios": {
                      "brand_name": "Base iOS"
                    }
                  },
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "targets": {
                        "ios": {
                          "brand_name": "Prod iOS"
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            schema = schema,
            sourceName = "base.json",
        )

        assertEquals(ConfigValue.StringValue("Base iOS"), parsed.defaults.targets.getValue("ios")[brandName])
        assertEquals(
            ConfigValue.StringValue("Prod iOS"),
            parsed.flavors.getValue("prod").targets.getValue("ios")[brandName],
        )
    }

    @Test
    fun resolvesTargetSpecificValuesWithExpectedPrecedence() {
        val resolved = resolver.resolve(
            defaultConfigJson = """
                {
                  "brand_name": "Base",
                  "targets": {
                    "jvm": {
                      "brand_name": "Base JVM"
                    }
                  },
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "brand_name": "Prod",
                      "targets": {
                        "jvm": {
                          "brand_name": "Prod JVM"
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            schema = schema,
            customConfigJson = """
                {
                  "targets": {
                    "jvm": {
                      "brand_name": "Custom JVM"
                    }
                  },
                  "flavors": {
                    "prod": {
                      "targets": {
                        "jvm": {
                          "brand_name": "Custom Prod JVM"
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            targetName = "jvm",
            defaultConfigSourceName = "base.json",
            customConfigSourceName = "custom.json",
        )

        assertEquals(
            ConfigValue.StringValue("Custom Prod JVM"),
            resolved.flavors.getValue("prod")[brandName],
        )
    }

    @Test
    fun parseRejectsInvalidJsonWithSourceName() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.parse(
                configJson = "{",
                schema = schema,
                sourceName = "broken.json",
            )
        }

        assertMessageContains(
            error,
            "Invalid JSON in source 'broken.json'",
        )
    }

    @Test
    fun parseRejectsNonObjectRoot() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.parse(
                configJson = "[]",
                schema = schema,
                sourceName = "broken.json",
            )
        }

        assertMessageContains(
            error,
            "source 'broken.json'",
            "path '$'",
            "expected object",
            "found array",
        )
    }

    @Test
    fun parseRequiresFlavorsObject() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.parse(
                configJson = """
                    {
                      "brand_name": "Example"
                    }
                """.trimIndent(),
                schema = schema,
                sourceName = "base.json",
            )
        }

        assertMessageContains(
            error,
            "Missing required 'flavors' object",
            "source 'base.json'",
            "path '$.flavors'",
        )
    }

    @Test
    fun parseRejectsNonObjectFlavorsValue() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.parse(
                configJson = """
                    {
                      "flavors": []
                    }
                """.trimIndent(),
                schema = schema,
                sourceName = "base.json",
            )
        }

        assertMessageContains(
            error,
            "source 'base.json'",
            "path '$.flavors'",
            "expected object",
            "found array",
        )
    }

    @Test
    fun parseRejectsNonObjectFlavorValue() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.parse(
                configJson = """
                    {
                      "flavors": {
                        "prod": true
                      }
                    }
                """.trimIndent(),
                schema = schema,
                sourceName = "base.json",
            )
        }

        assertMessageContains(
            error,
            "source 'base.json'",
            "flavor 'prod'",
            "path '$.flavors.prod'",
            "expected object",
            "found boolean",
        )
    }

    @Test
    fun mergesCustomConfigUsingExpectedPrecedence() {
        val resolved = resolver.resolve(
            defaultConfigJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "feature_search_enabled": false
                    }
                  },
                  "brand_name": "Base",
                  "feature_search_enabled": true
                }
            """.trimIndent(),
            schema = schema,
            customConfigJson = """
                {
                  "flavors": {
                    "prod": {
                      "brand_name": "Custom Flavor"
                    }
                  },
                  "brand_name": "Custom Default"
                }
            """.trimIndent(),
        )

        val flavor = resolved.flavors.getValue("prod")
        assertEquals(ConfigValue.StringValue("Custom Flavor"), flavor[brandName])
        assertEquals(ConfigValue.BooleanValue(false), flavor[searchEnabled])
    }

    @Test
    fun allowsProtectedKeysToVaryAcrossBaseFlavors() {
        val resolved = resolver.resolve(
            defaultConfigJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "api_secret": "prod-secret"
                    }
                  },
                  "api_secret": "shared-secret"
                }
            """.trimIndent(),
            schema = schema,
        )

        assertEquals(
            ConfigValue.StringValue("prod-secret"),
            resolved.flavors.getValue("prod")[apiSecret],
        )
    }

    @Test
    fun rejectsProtectedKeyInCustomConfig() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod",
                          "api_secret": "prod-secret"
                        }
                      }
                    }
                """.trimIndent(),
                schema = schema,
                customConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "api_secret": "custom-secret"
                        }
                      }
                    }
                """.trimIndent(),
                defaultConfigSourceName = "base.json",
                customConfigSourceName = "custom.json",
            )
        }

        assertMessageContains(
            error,
            "Key 'api_secret'",
            "source 'custom.json'",
            "path '$.flavors.prod.api_secret'",
            "preventOverride",
            "source 'base.json'",
        )
    }

    @Test
    fun rejectsCustomFlavorThatDoesNotExistInBaseConfig() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      }
                    }
                """.trimIndent(),
                schema = schema,
                customConfigJson = """
                    {
                      "flavors": {
                        "dev": {
                          "bundle_id": "com.example.dev"
                        }
                      }
                    }
                """.trimIndent(),
            )
        }

        assertTrue(error.message!!.contains("Flavor 'dev'"))
    }

    @Test
    fun rejectsUnknownKeyInBaseConfig() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      },
                      "unknown_key": true
                    }
                """.trimIndent(),
                schema = schema,
                customConfigJson = null,
                defaultConfigSourceName = "base.json",
            )
        }

        assertMessageContains(
            error,
            "Unknown key 'unknown_key'",
            "source 'base.json'",
            "path '$.unknown_key'",
        )
    }

    @Test
    fun rejectsUnknownKeyInCustomConfig() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      }
                    }
                """.trimIndent(),
                schema = schema,
                customConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "brand_nam": true
                        }
                      }
                    }
                """.trimIndent(),
                defaultConfigSourceName = "base.json",
                customConfigSourceName = "custom.json",
            )
        }

        assertMessageContains(
            error,
            "Unknown key 'brand_nam'",
            "source 'custom.json'",
            "flavor 'prod'",
            "path '$.flavors.prod.brand_nam'",
            "Did you mean 'brand_name'?",
        )
    }

    @Test
    fun rejectsWrongScalarTypes() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      },
                      "max_workspace_count": "three"
                    }
                """.trimIndent(),
                schema = schema,
                customConfigJson = null,
                defaultConfigSourceName = "base.json",
            )
        }

        assertMessageContains(
            error,
            "source 'base.json'",
            "path '$.max_workspace_count'",
            "expected int",
            "found string",
        )
    }

    @Test
    fun rejectsWrongListShape() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      },
                      "support_links": ["valid", 12]
                    }
                """.trimIndent(),
                schema = schema,
                customConfigJson = null,
                defaultConfigSourceName = "base.json",
            )
        }

        assertMessageContains(
            error,
            "source 'base.json'",
            "path '$.support_links[1]'",
            "expected string",
            "found int",
        )
    }

    @Test
    fun rejectsWrongMapShape() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      },
                      "regional_support_links": {
                        "example.com": "wrong"
                      }
                    }
                """.trimIndent(),
                schema = schema,
                customConfigJson = null,
                defaultConfigSourceName = "base.json",
            )
        }

        assertMessageContains(
            error,
            "source 'base.json'",
            "path '$.regional_support_links[\"example.com\"]'",
            "expected list of strings",
            "found string",
        )
    }

    @Test
    fun parsesRicherTypes() {
        val resolved = resolver.resolve(
            defaultConfigJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "release_stage": "beta"
                    }
                  },
                  "max_cache_bytes": 9876543210,
                  "support_labels": {
                    "channel": "stable"
                  }
                }
            """.trimIndent(),
            schema = schema,
        )

        val flavor = resolved.flavors.getValue("prod")
        assertEquals(ConfigValue.LongValue(9876543210), flavor[maxCacheBytes])
        assertNull(flavor[rolloutRatio])
        assertEquals(ConfigValue.StringMapValue(mapOf("channel" to "stable")), flavor[supportLabels])
        assertEquals(ConfigValue.EnumValue("BETA"), flavor[releaseStage])
    }

    @Test
    fun normalizesEnumValuesWithWhitespaceAndSeparators() {
        val resolved = resolver.resolve(
            defaultConfigJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod",
                      "release_stage": " pre Prod-2 "
                    }
                  }
                }
            """.trimIndent(),
            schema = schema,
        )

        assertEquals(
            ConfigValue.EnumValue("PRE_PROD_2"),
            resolved.flavors.getValue("prod")[releaseStage],
        )
    }

    @Test
    fun rejectsEnumValuesWithoutLettersOrDigits() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod",
                          "release_stage": "---"
                        }
                      }
                    }
                """.trimIndent(),
                schema = schema,
                customConfigJson = null,
                defaultConfigSourceName = "base.json",
            )
        }

        assertMessageContains(
            error,
            "Invalid value for key 'release_stage'",
            "source 'base.json'",
            "path '$.flavors.prod.release_stage'",
            "Enum values must contain at least one letter or digit.",
        )
    }

    @Test
    fun explicitNullOverridesLowerPrecedenceValuesForNullableKeys() {
        val resolved = resolver.resolve(
            defaultConfigJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  },
                  "support_email": "base@example.com"
                }
            """.trimIndent(),
            schema = schema,
            customConfigJson = """
                {
                  "flavors": {
                    "prod": {
                      "support_email": null
                    }
                  }
                }
            """.trimIndent(),
        )

        assertEquals(
            ConfigValue.NullValue(ConfigValueKind.STRING),
            resolved.flavors.getValue("prod")[supportEmail],
        )
    }

    @Test
    fun rejectsNullForNonNullableKeys() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      },
                      "brand_name": null
                    }
                """.trimIndent(),
                schema = schema,
            )
        }

        assertMessageContains(
            error,
            "path '$.brand_name'",
            "expected string",
            "found null",
        )
    }

    @Test
    fun rejectsWrongStringMapEntryType() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {
                          "bundle_id": "com.example.prod"
                        }
                      },
                      "support_labels": {
                        "channel": false
                      }
                    }
                """.trimIndent(),
                schema = schema,
                customConfigJson = null,
                defaultConfigSourceName = "base.json",
            )
        }

        assertMessageContains(
            error,
            "source 'base.json'",
            "path '$.support_labels.channel'",
            "expected string",
            "found boolean",
        )
    }

    @Test
    fun leavesOptionalValuesNullWhenAbsent() {
        val resolved = resolver.resolve(
            defaultConfigJson = """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
            schema = schema,
        )

        assertNull(resolved.flavors.getValue("prod")[searchEnabled])
    }

    @Test
    fun requiresConsumerDefinedRequiredKeysPerResolvedFlavor() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.resolve(
                defaultConfigJson = """
                    {
                      "flavors": {
                        "prod": {}
                      }
                    }
                """.trimIndent(),
                schema = schema,
            )
        }

        assertTrue(error.message!!.contains("bundle_id"))
    }
}
