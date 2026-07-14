package io.kayan.gradle

import arrow.core.Either
import io.kayan.ConfigValueKind
import io.kayan.SchemaError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class KayanSchemaEntrySpecTypedTest {
    @Test
    fun deserializeEitherReturnsStructuredInvalidJsonError() {
        when (val result = KayanSchemaEntrySpec.deserializeEither("{", 3)) {
            is Either.Left -> {
                val error = assertIs<SchemaError.InvalidSerializedJson>(result.value)
                assertEquals(3, error.entryIndex)
                assertTrue(error.detail.orEmpty().isNotBlank())
            }

            is Either.Right -> fail("Expected an invalid serialized JSON error.")
        }
    }

    @Test
    fun deserializeEitherReturnsStructuredInvalidKindError() {
        val serialized = serializedEntry().replace("\"STRING\"", "\"URI\"")

        when (val result = KayanSchemaEntrySpec.deserializeEither(serialized, 2)) {
            is Either.Left -> {
                val error = assertIs<SchemaError.InvalidEnumKind>(result.value)
                assertEquals(2, error.entryIndex)
                assertEquals("URI", error.rawValue)
            }

            is Either.Right -> fail("Expected an invalid schema kind error.")
        }
    }

    @Test
    fun deserializeEitherReportsEveryMissingRequiredField() {
        listOf("kind", "jsonKey", "propertyName", "required", "nullable").forEach { missingField ->
            val serialized = serializedEntry(excludedField = missingField)

            when (val result = KayanSchemaEntrySpec.deserializeEither(serialized, 4)) {
                is Either.Left -> {
                    val error = assertIs<SchemaError.MissingRequiredField>(result.value)
                    assertEquals(4, error.entryIndex)
                    assertEquals(missingField, error.fieldName)
                }

                is Either.Right -> fail("Expected missing schema field '$missingField'.")
            }
        }
    }

    @Test
    fun deserializeEitherRejectsNonPrimitiveRequiredFields() {
        val serialized = serializedEntry().replace("\"required\":false", "\"required\":{}")

        when (val result = KayanSchemaEntrySpec.deserializeEither(serialized, 0)) {
            is Either.Left -> {
                val error = assertIs<SchemaError.MissingRequiredField>(result.value)
                assertEquals("required", error.fieldName)
            }

            is Either.Right -> fail("Expected invalid required field encoding.")
        }
    }

    @Test
    fun throwingSchemaEntryHelpersPreserveValidationMessages() {
        val deserializeError = assertFailsWith<IllegalStateException> {
            KayanSchemaEntrySpec.deserialize("[]")
        }
        val schemaError = assertFailsWith<IllegalStateException> {
            KayanSchemaEntrySpec.toSchema(emptyList())
        }

        assertTrue(deserializeError.message.orEmpty().contains("Invalid serialized Kayan schema entry"))
        assertTrue(schemaError.message.orEmpty().contains("Config schema must contain at least one definition"))
    }

    @Test
    fun toSchemaEitherRejectsBlankPropertyNameAndMissingEnumType() {
        val blankPropertyName = KayanSchemaEntrySpec(
            jsonKey = "bundle_id",
            propertyName = "   ",
            kind = ConfigValueKind.STRING,
            required = false,
            nullable = false,
        ).serialize()
        val missingEnumType = KayanSchemaEntrySpec(
            jsonKey = "release_stage",
            propertyName = "RELEASE_STAGE",
            kind = ConfigValueKind.ENUM,
            required = false,
            nullable = false,
        ).serialize()

        when (val result = KayanSchemaEntrySpec.toSchemaEither(listOf(blankPropertyName, missingEnumType))) {
            is Either.Left -> {
                assertTrue(result.value.any { it is SchemaError.BlankPropertyName })
                assertTrue(result.value.any { it is SchemaError.MissingEnumType })
            }

            is Either.Right -> fail("Expected blank property and missing enum type errors.")
        }
    }

    @Test
    fun deserializeDefaultsPreventOverrideToFalseWhenFieldIsMissing() {
        val spec = KayanSchemaEntrySpec.deserialize(
            """
                {
                  "jsonKey": "bundle_id",
                  "propertyName": "BUNDLE_ID",
                  "kind": "${ConfigValueKind.STRING.name}",
                  "required": false,
                  "nullable": false
                }
            """.trimIndent(),
        )

        assertFalse(spec.preventOverride)
    }

    @Test
    fun deserializeAndToSchemaPreservePreventOverrideAndOptionalFields() {
        val serialized = KayanSchemaEntrySpec(
            jsonKey = "release_stage",
            propertyName = "RELEASE_STAGE",
            kind = ConfigValueKind.ENUM,
            required = true,
            nullable = false,
            enumTypeName = "sample.ReleaseStage",
            adapterClassName = "sample.ReleaseStageAdapter",
            preventOverride = true,
        ).serialize()

        val spec = KayanSchemaEntrySpec.deserialize(serialized)
        val schema = KayanSchemaEntrySpec.toSchema(listOf(serialized))
        val definition = schema.definitionFor("release_stage")

        assertEquals("sample.ReleaseStage", spec.enumTypeName)
        assertEquals("sample.ReleaseStageAdapter", spec.adapterClassName)
        assertTrue(spec.preventOverride)
        assertEquals(true, definition?.preventOverride)
        assertEquals("sample.ReleaseStageAdapter", definition?.adapterClassName)
        assertEquals("sample.ReleaseStage", definition?.enumTypeName)
    }

    @Test
    fun deserializeEitherReturnsStructuredInvalidRootError() {
        when (val result = KayanSchemaEntrySpec.deserializeEither("[]", 0)) {
            is Either.Left -> {
                val error = assertIs<SchemaError.InvalidSerializedRoot>(result.value)
                assertEquals(0, error.entryIndex)
                assertEquals("Invalid serialized Kayan schema entry.", error.message())
            }

            is Either.Right -> fail("Expected a schema error.")
        }
    }

    @Test
    fun toSchemaEitherAccumulatesIndependentValidationErrors() {
        val serializedEntry = """
            {
              "jsonKey": "",
              "propertyName": "1_INVALID",
              "kind": "${ConfigValueKind.STRING.name}",
              "required": true,
              "nullable": true,
              "enumTypeName": "sample.Stage",
              "adapterClassName": ""
            }
        """.trimIndent()

        when (val result = KayanSchemaEntrySpec.toSchemaEither(listOf(serializedEntry))) {
            is Either.Left -> {
                assertTrue(result.value.any { it is SchemaError.BlankJsonKey })
                assertTrue(result.value.any { it is SchemaError.InvalidPropertyName })
                assertTrue(result.value.any { it is SchemaError.RequiredAndNullable })
                assertTrue(result.value.any { it is SchemaError.EnumTypeOnNonEnum })
                assertTrue(result.value.any { it is SchemaError.BlankAdapterClassName })
            }

            is Either.Right -> fail("Expected accumulated schema validation errors.")
        }
    }

    @Test
    fun toSchemaEitherRejectsInvalidEnumTypeName() {
        val serializedEntry = KayanSchemaEntrySpec(
            jsonKey = "release_stage",
            propertyName = "RELEASE_STAGE",
            kind = ConfigValueKind.ENUM,
            required = false,
            nullable = false,
            enumTypeName = "sample.1Stage",
        ).serialize()

        when (val result = KayanSchemaEntrySpec.toSchemaEither(listOf(serializedEntry))) {
            is Either.Left -> {
                val error = assertIs<SchemaError.InvalidEnumTypeName>(
                    result.value.first { it is SchemaError.InvalidEnumTypeName },
                )

                assertEquals("release_stage", error.jsonKey)
                assertEquals("sample.1Stage", error.enumTypeName)
            }

            is Either.Right -> fail("Expected invalid enum type schema validation error.")
        }
    }

    @Test
    fun toSchemaEitherAccumulatesDuplicateSchemaErrors() {
        val first = KayanSchemaEntrySpec(
            jsonKey = "bundle_id",
            propertyName = "BUNDLE_ID",
            kind = ConfigValueKind.STRING,
            required = false,
            nullable = false,
        ).serialize()
        val second = KayanSchemaEntrySpec(
            jsonKey = "bundle_id",
            propertyName = "BUNDLE_ID",
            kind = ConfigValueKind.STRING,
            required = false,
            nullable = false,
        ).serialize()

        when (val result = KayanSchemaEntrySpec.toSchemaEither(listOf(first, second))) {
            is Either.Left -> {
                val duplicateJsonKeys = assertIs<SchemaError.DuplicateJsonKeys>(
                    result.value.first { it is SchemaError.DuplicateJsonKeys },
                )
                val duplicatePropertyNames = assertIs<SchemaError.DuplicatePropertyNames>(
                    result.value.first { it is SchemaError.DuplicatePropertyNames },
                )

                assertEquals(listOf("bundle_id"), duplicateJsonKeys.jsonKeys)
                assertEquals(listOf("BUNDLE_ID"), duplicatePropertyNames.propertyNames)
            }

            is Either.Right -> fail("Expected duplicate schema validation errors.")
        }
    }

    @Test
    fun toSchemaEitherRejectsReservedJsonKeys() {
        val targets = KayanSchemaEntrySpec(
            jsonKey = "targets",
            propertyName = "TARGETS",
            kind = ConfigValueKind.STRING,
            required = false,
            nullable = false,
        ).serialize()
        val flavors = KayanSchemaEntrySpec(
            jsonKey = "flavors",
            propertyName = "FLAVORS",
            kind = ConfigValueKind.STRING,
            required = false,
            nullable = false,
        ).serialize()

        when (val result = KayanSchemaEntrySpec.toSchemaEither(listOf(targets, flavors))) {
            is Either.Left -> {
                val reservedJsonKeys = assertIs<SchemaError.ReservedJsonKeys>(
                    result.value.first { it is SchemaError.ReservedJsonKeys },
                )

                assertEquals(listOf("targets", "flavors"), reservedJsonKeys.jsonKeys)
                assertEquals(
                    "Config schema contains reserved jsonKey values: 'targets', 'flavors'.",
                    reservedJsonKeys.message(),
                )
            }

            is Either.Right -> fail("Expected reserved jsonKey schema validation error.")
        }
    }

    private fun serializedEntry(excludedField: String? = null): String {
        val fields = linkedMapOf(
            "jsonKey" to "\"bundle_id\"",
            "propertyName" to "\"BUNDLE_ID\"",
            "kind" to "\"STRING\"",
            "required" to "false",
            "nullable" to "false",
        )

        return fields
            .filterKeys { it != excludedField }
            .entries
            .joinToString(prefix = "{", postfix = "}") { (key, value) -> "\"$key\":$value" }
    }
}
