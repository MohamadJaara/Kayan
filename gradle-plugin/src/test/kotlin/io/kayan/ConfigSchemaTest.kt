package io.kayan

import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
