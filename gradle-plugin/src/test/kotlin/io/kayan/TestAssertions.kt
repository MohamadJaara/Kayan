package io.kayan

import kotlin.test.assertTrue

internal fun assertMessageContains(
    error: Throwable,
    vararg parts: String,
) {
    val message = error.message.orEmpty()
    parts.forEach { part ->
        assertTrue(message.contains(part), "Expected <$message> to contain <$part>.")
    }
}
