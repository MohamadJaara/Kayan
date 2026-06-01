package io.kayan.gradle

import arrow.core.Either
import io.kayan.ConfigValueKind
import io.kayan.SchemaError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class KayanSchemaEntrySpecTypedTest {
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
}
