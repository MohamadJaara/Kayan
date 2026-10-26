package io.kayan.gradle

import arrow.core.Either
import io.kayan.ConfigError
import io.kayan.ConfigFormat
import io.kayan.ConfigValueKind
import io.kayan.KayanValidationMode
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KayanConfigValidationTest {
    @Test
    fun validatesEveryFlavorWithCustomJsonOverrides() {
        val summary = assertIs<Either.Right<String>>(
            validate(
                base = """{"api_url":"base","flavors":{"prod":{},"staging":{}}}""",
                custom = """{"flavors":{"staging":{"api_url":"custom"}}}""",
            ),
        ).value

        assertTrue(summary.contains("2 flavor(s) without target overlays"))
    }

    @Test
    fun resolvesRequiredValuesOnlyAfterConfiguredTargetOverlays() {
        val result = validate(
            base = """
                {"targets":{"jvm":{"api_url":"jvm"},"ios":{"api_url":"ios"}},
                 "flavors":{"prod":{},"staging":{}}}
            """.trimIndent(),
            targets = listOf("jvm", "ios", "jvm"),
        )

        val summary = assertIs<Either.Right<String>>(result).value
        assertTrue(summary.contains("2 flavor(s) for targets 'jvm', 'ios'"))
    }

    @Test
    fun normalizesConfiguredTargetsLikeSourceGeneration() {
        val result = validate(
            base = """{"targets":{"jvm":{"api_url":"jvm"}},"flavors":{"prod":{}}}""",
            targets = listOf(" jvm ", "jvm"),
        )

        val summary = assertIs<Either.Right<String>>(result).value
        assertTrue(summary.contains("1 flavor(s) for targets 'jvm'"))
    }

    @Test
    fun preservesInferredTargetNamesFromConfigurationFiles() {
        val result = validate(
            base = """{"targets":{" jvm ":{"api_url":"jvm"}},"flavors":{"prod":{}}}""",
        )

        val summary = assertIs<Either.Right<String>>(result).value
        assertTrue(summary.contains("1 flavor(s) for targets ' jvm '"))
    }

    @Test
    fun infersTargetsFromDefaultsAndAllBaseFlavorsWithoutMappings() {
        val result = validate(
            base = """
                {"targets":{"jvm":{"api_url":"jvm"}},"flavors":{
                 "prod":{"targets":{"ios":{"api_url":"ios-prod"}}},
                 "staging":{"targets":{"ios":{"api_url":"ios-staging"}}}}}
            """.trimIndent(),
        )

        val summary = assertIs<Either.Right<String>>(result).value
        assertTrue(summary.contains("2 flavor(s) for targets 'jvm', 'ios'"))
    }

    @Test
    fun detectsMissingRequiredValueInAnotherBaseFlavor() {
        val error = resolutionError(
            validate(base = """{"flavors":{"prod":{"api_url":"prod"},"staging":{}}}"""),
        )

        assertEquals("staging", assertIs<ConfigError.MissingRequiredResolvedKey>(error).flavorName)
    }

    @Test
    fun detectsMissingRequiredValueInEveryConfiguredTargetAndFlavor() {
        val error = resolutionError(
            validate(
                base = """
                    {"targets":{"jvm":{"api_url":"jvm"}},"flavors":{
                     "prod":{"targets":{"ios":{"api_url":"ios"}}},"staging":{}}}
                """.trimIndent(),
                targets = listOf("jvm", "ios"),
            ),
        )

        val missing = assertIs<ConfigError.MissingRequiredResolvedKey>(error)
        assertEquals("staging", missing.flavorName)
        assertEquals("ios", missing.targetName)
    }

    @Test
    fun configuredTargetsMayInheritValuesWithoutDedicatedOverlays() {
        assertIs<Either.Right<String>>(
            validate(
                base = """{"api_url":"base","flavors":{"prod":{}}}""",
                targets = listOf("jvm", "ios"),
            ),
        )
    }

    @Test
    fun validatesYamlWithAutoFormatAndCustomTargetOverlays() {
        val result = validate(
            base = """
                targets:
                  jvm: {}
                flavors:
                  prod: {}
                  staging: {}
            """.trimIndent(),
            custom = """
                targets:
                  jvm:
                    api_url: custom
                flavors: {}
            """.trimIndent(),
            format = ConfigFormat.AUTO,
            fileExtension = "yaml",
        )

        assertTrue(assertIs<Either.Right<String>>(result).value.contains("2 flavor(s) for targets 'jvm'"))
    }

    @Test
    fun respectsExplicitYamlFormatAndRejectsMismatchedFileExtensions() {
        val yaml = """
            api_url: base
            flavors:
              prod: {}
        """.trimIndent()

        assertIs<Either.Right<String>>(validate(yaml, format = ConfigFormat.YAML, fileExtension = "yaml"))
        assertIs<ConfigError.ConfigFormatMismatch>(
            resolutionError(validate(yaml, format = ConfigFormat.YAML, fileExtension = "json")),
        )
    }

    @Test
    fun checksInferredTargetsAcrossEveryFlavorWithoutMappings() {
        val result = validate(
            base = """
                {"flavors":{"prod":{"targets":{"ios":{"api_url":"ios"}}},"staging":{}}}
            """.trimIndent(),
        )

        val error = assertIs<ConfigError.MissingRequiredResolvedKey>(resolutionError(result))
        assertEquals("staging", error.flavorName)
        assertEquals("ios", error.targetName)
    }

    @Test
    fun rejectsUnknownCustomFlavorsAndTargets() {
        val base = """{"api_url":"base","targets":{"jvm":{}},"flavors":{"prod":{}}}"""
        assertIs<ConfigError.UnknownFlavorInCustomConfig>(
            resolutionError(validate(base, custom = """{"flavors":{"typo":{}}}""")),
        )
        assertIs<ConfigError.UnknownTargetInCustomConfig>(
            resolutionError(validate(base, custom = """{"flavors":{"prod":{"targets":{"typo":{}}}}}""")),
        )
    }

    @Test
    fun rejectsProtectedOverridesInTargetSections() {
        val result = validate(
            base = """{"api_url":"base","targets":{"jvm":{}},"flavors":{"prod":{}}}""",
            custom = """{"flavors":{"prod":{"targets":{"jvm":{"api_url":"custom"}}}}}""",
            preventOverride = true,
        )

        assertIs<ConfigError.PreventedCustomOverride>(resolutionError(result))
    }

    @Test
    fun checksTypesEvenInTargetsWithoutGenerationMappings() {
        val result = validate(
            base = """
                {"api_url":"base","targets":{"unused":{"api_url":42}},"flavors":{"prod":{}}}
            """.trimIndent(),
            targets = listOf("jvm"),
        )

        assertIs<ConfigError.InvalidType>(resolutionError(result))
    }

    @Test
    fun respectsSubsetAndStrictUnknownKeyPolicies() {
        val base = """{"api_url":"base","flavors":{"prod":{"unknown":true}}}"""

        assertIs<Either.Right<String>>(validate(base))
        assertIs<ConfigError.UnknownKey>(resolutionError(validate(base, mode = KayanValidationMode.STRICT)))
    }

    private fun resolutionError(result: Either<GenerationError, String>): ConfigError =
        assertIs<GenerationError.ConfigResolutionFailure>(assertIs<Either.Left<GenerationError>>(result).value).error

    private fun validate(
        base: String,
        custom: String? = null,
        targets: List<String> = emptyList(),
        mode: KayanValidationMode = KayanValidationMode.SUBSET,
        format: ConfigFormat = ConfigFormat.JSON,
        fileExtension: String = "json",
        preventOverride: Boolean = false,
    ): Either<GenerationError, String> {
        val directory = createTempDirectory(prefix = "kayan-validation-test").toFile()
        val baseFile = File(directory, "base.$fileExtension").apply { writeText(base) }
        val customFile = custom?.let { File(directory, "custom.$fileExtension").apply { writeText(it) } }
        val schema = requireSchema(
            listOf(
                KayanSchemaEntrySpec(
                    jsonKey = "api_url",
                    propertyName = "API_URL",
                    kind = ConfigValueKind.STRING,
                    required = true,
                    preventOverride = preventOverride,
                    nullable = false,
                ).serialize(),
            ),
        )
        return validateConfigEither(schema, baseFile, customFile, format, mode, targets)
    }
}
