package io.kayan

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultConfigResolverJsonSyntaxTest {
    private val resolver = DefaultConfigResolver()
    private val value = ConfigDefinition(
        jsonKey = "value",
        propertyName = "VALUE",
        kind = ConfigValueKind.STRING,
        required = true,
    )
    private val schema = ConfigSchema(listOf(value))

    @Test
    fun rejectsBarewordRequiredStringInBothValidationModes() {
        KayanValidationMode.entries.forEach { mode ->
            val result = resolver.resolveEither(
                defaultConfigJson = """{"flavors":{"prod":{}},"value":bareword}""",
                customConfigJson = null,
                schema = schema,
                defaultConfigSourceName = "base.json",
                validationMode = mode,
            )

            assertSyntaxError(result, "base.json")
        }
    }

    @Test
    fun rejectsMalformedSyntaxInIgnoredSubsetFieldsAndUnselectedFlavors() {
        listOf(
            """{"flavors":{"prod":{}},"value":"valid","ignored":{"nested":[bareword]}}""",
            """{"flavors":{"prod":{},"unused":{"ignored":01}},"value":"valid"}""",
            """{"flavors":{"prod":{}},"value":"valid","targets":{"unused":{"ignored":truee}}}""",
        ).forEach { config ->
            val result = resolver.resolveEither(
                defaultConfigJson = config,
                customConfigJson = null,
                schema = schema,
                defaultConfigSourceName = "shared.json",
                validationMode = KayanValidationMode.SUBSET,
            )

            assertSyntaxError(result, "shared.json")
        }
    }

    @Test
    fun rejectsMalformedCustomSourceWithItsOwnSourceContext() {
        KayanValidationMode.entries.forEach { mode ->
            val result = resolver.resolveEither(
                defaultConfigJson = """{"flavors":{"prod":{}},"value":"valid"}""",
                customConfigJson = """{"flavors":{"prod":{"value":bareword}}}""",
                schema = schema,
                defaultConfigSourceName = "base.json",
                customConfigSourceName = "override.json",
                validationMode = mode,
            )

            assertSyntaxError(result, "override.json")
        }
    }

    @Test
    fun publicResolverReportsSyntaxFailureWithSourceContext() {
        val error = assertFailsWith<ConfigValidationException> {
            resolver.parse(
                configJson = """{"flavors":{"prod":{}},"value":bareword}""",
                schema = schema,
                sourceName = "broken.json",
            )
        }

        assertTrue(error.message.orEmpty().contains("Invalid JSON in source 'broken.json'"))
    }

    @Test
    fun resolvesQuotedStringsWithEscapesInBothValidationModes() {
        KayanValidationMode.entries.forEach { mode ->
            val resolved = resolver.resolve(
                defaultConfigJson = """{"flavors":{"prod":{}},"value":"bareword\n\u96ea"}""",
                schema = schema,
                customConfigJson = null,
                validationMode = mode,
            )

            assertEquals(ConfigValue.StringValue("bareword\n雪"), resolved.flavors.getValue("prod").values[value])
        }
    }

    private fun assertSyntaxError(result: Either<ConfigError, *>, sourceName: String) {
        val error = assertIs<ConfigError.InvalidConfigSyntax>(assertIs<Either.Left<ConfigError>>(result).value)
        assertEquals(sourceName, error.sourceName)
        assertEquals("JSON", error.formatName)
    }
}
