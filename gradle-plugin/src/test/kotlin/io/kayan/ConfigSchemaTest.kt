package io.kayan

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigSchemaTest {
    @Test
    fun rejectsReservedJsonKeys() {
        assertFailsWith<IllegalArgumentException> {
            ConfigSchema(
                listOf(
                    ConfigDefinition(
                        jsonKey = "targets",
                        propertyName = "TARGETS",
                        kind = ConfigValueKind.STRING,
                    ),
                ),
            )
        }
    }

    @Test
    fun rejectsInvalidGeneratedKotlinNames() {
        val propertyError = assertFailsWith<IllegalArgumentException> {
            ConfigDefinition(
                jsonKey = "bundle_id",
                propertyName = "1_BUNDLE_ID",
                kind = ConfigValueKind.STRING,
            )
        }
        assertTrue(
            propertyError.message.orEmpty().contains("propertyName must be a valid Kotlin identifier"),
        )

        val enumTypeError = assertFailsWith<IllegalArgumentException> {
            ConfigDefinition(
                jsonKey = "release_stage",
                propertyName = "RELEASE_STAGE",
                kind = ConfigValueKind.ENUM,
                enumTypeName = "sample.1Stage",
            )
        }
        assertTrue(
            enumTypeError.message.orEmpty().contains("enumTypeName must be a valid Kotlin qualified name"),
        )
    }
}
