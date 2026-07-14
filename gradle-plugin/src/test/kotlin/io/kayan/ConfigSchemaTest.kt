package io.kayan

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigSchemaTest {
    @Test
    fun rejectsBlankDefinitionNames() {
        val jsonKeyError = assertFailsWith<IllegalArgumentException> {
            ConfigDefinition("   ", "BUNDLE_ID", ConfigValueKind.STRING)
        }
        val propertyNameError = assertFailsWith<IllegalArgumentException> {
            ConfigDefinition("bundle_id", "   ", ConfigValueKind.STRING)
        }

        assertTrue(jsonKeyError.message.orEmpty().contains("jsonKey must not be blank"))
        assertTrue(propertyNameError.message.orEmpty().contains("propertyName must not be blank"))
    }

    @Test
    fun rejectsRequiredNullableDefinitions() {
        val error = assertFailsWith<IllegalArgumentException> {
            ConfigDefinition(
                jsonKey = "support_email",
                propertyName = "SUPPORT_EMAIL",
                kind = ConfigValueKind.STRING,
                required = true,
                nullable = true,
            )
        }

        assertTrue(error.message.orEmpty().contains("cannot be both required and nullable"))
    }

    @Test
    fun rejectsIncompatibleEnumMetadata() {
        val enumTypeOnStringError = assertFailsWith<IllegalArgumentException> {
            ConfigDefinition(
                jsonKey = "release_stage",
                propertyName = "RELEASE_STAGE",
                kind = ConfigValueKind.STRING,
                enumTypeName = "sample.ReleaseStage",
            )
        }
        val missingEnumTypeError = assertFailsWith<IllegalArgumentException> {
            ConfigDefinition(
                jsonKey = "release_stage",
                propertyName = "RELEASE_STAGE",
                kind = ConfigValueKind.ENUM,
            )
        }

        assertTrue(enumTypeOnStringError.message.orEmpty().contains("only set enumTypeName for enum values"))
        assertTrue(missingEnumTypeError.message.orEmpty().contains("must declare enumTypeName"))
    }

    @Test
    fun rejectsBlankAdapterClassName() {
        val error = assertFailsWith<IllegalArgumentException> {
            ConfigDefinition(
                jsonKey = "release_stage",
                propertyName = "RELEASE_STAGE",
                kind = ConfigValueKind.STRING,
                adapterClassName = "   ",
            )
        }

        assertTrue(error.message.orEmpty().contains("adapterClassName must not be blank"))
    }

    @Test
    fun rejectsEmptyAndDuplicateSchemas() {
        val emptyError = assertFailsWith<IllegalArgumentException> {
            ConfigSchema(emptyList())
        }
        val duplicateKeyError = assertFailsWith<IllegalArgumentException> {
            ConfigSchema(
                listOf(
                    ConfigDefinition("bundle_id", "BUNDLE_ID", ConfigValueKind.STRING),
                    ConfigDefinition("bundle_id", "SECOND_BUNDLE_ID", ConfigValueKind.STRING),
                ),
            )
        }
        val duplicatePropertyError = assertFailsWith<IllegalArgumentException> {
            ConfigSchema(
                listOf(
                    ConfigDefinition("bundle_id", "SHARED_VALUE", ConfigValueKind.STRING),
                    ConfigDefinition("brand_name", "SHARED_VALUE", ConfigValueKind.STRING),
                ),
            )
        }

        assertTrue(emptyError.message.orEmpty().contains("must contain at least one definition"))
        assertTrue(duplicateKeyError.message.orEmpty().contains("duplicate jsonKey"))
        assertTrue(duplicatePropertyError.message.orEmpty().contains("duplicate propertyName"))
    }

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
