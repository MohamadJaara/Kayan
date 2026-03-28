package io.kayan.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginConfigurationErrorTargetTest {

    // -------- BlankTargetSourceSetName --------

    @Test
    fun blankTargetSourceSetNameMessageContainsIndex() {
        val error = PluginConfigurationError.BlankTargetSourceSetName(index = 3)

        assertTrue(error.message().contains("3"))
    }

    @Test
    fun blankTargetSourceSetNameMessageDescribesConstraint() {
        val error = PluginConfigurationError.BlankTargetSourceSetName(index = 0)

        assertTrue(error.message().contains("non-blank"))
        assertTrue(error.message().contains("source set"))
    }

    @Test
    fun blankTargetSourceSetNameHasNullCause() {
        val error = PluginConfigurationError.BlankTargetSourceSetName(index = 0)

        assertNull(error.cause)
    }

    @Test
    fun blankTargetSourceSetNameIndexZeroAndNonZeroProduceDifferentMessages() {
        val first = PluginConfigurationError.BlankTargetSourceSetName(index = 0)
        val second = PluginConfigurationError.BlankTargetSourceSetName(index = 5)

        assertTrue(first.message() != second.message())
    }

    // -------- BlankTargetName --------

    @Test
    fun blankTargetNameMessageContainsIndex() {
        val error = PluginConfigurationError.BlankTargetName(index = 2)

        assertTrue(error.message().contains("2"))
    }

    @Test
    fun blankTargetNameMessageDescribesConstraint() {
        val error = PluginConfigurationError.BlankTargetName(index = 0)

        assertTrue(error.message().contains("non-blank"))
        assertTrue(error.message().contains("target"))
    }

    @Test
    fun blankTargetNameHasNullCause() {
        val error = PluginConfigurationError.BlankTargetName(index = 0)

        assertNull(error.cause)
    }

    // -------- DuplicateTargetSourceSet --------

    @Test
    fun duplicateTargetSourceSetMessageContainsSourceSetName() {
        val error = PluginConfigurationError.DuplicateTargetSourceSet(
            sourceSetName = "sharedMain",
            firstTargetName = "ios",
            duplicateTargetName = "android",
        )

        assertTrue(error.message().contains("sharedMain"))
    }

    @Test
    fun duplicateTargetSourceSetMessageContainsFirstAndDuplicateTargetNames() {
        val error = PluginConfigurationError.DuplicateTargetSourceSet(
            sourceSetName = "sharedMain",
            firstTargetName = "ios",
            duplicateTargetName = "android",
        )

        assertTrue(error.message().contains("ios"))
        assertTrue(error.message().contains("android"))
    }

    @Test
    fun duplicateTargetSourceSetMessageMentionsConflict() {
        val error = PluginConfigurationError.DuplicateTargetSourceSet(
            sourceSetName = "sharedMain",
            firstTargetName = "ios",
            duplicateTargetName = "android",
        )

        assertTrue(error.message().contains("more than once"))
    }

    @Test
    fun duplicateTargetSourceSetHasNullCause() {
        val error = PluginConfigurationError.DuplicateTargetSourceSet(
            sourceSetName = "sharedMain",
            firstTargetName = "ios",
            duplicateTargetName = "android",
        )

        assertNull(error.cause)
    }

    // -------- MissingKotlinSourceSet --------

    @Test
    fun missingKotlinSourceSetMessageContainsMissingSourceSetName() {
        val error = PluginConfigurationError.MissingKotlinSourceSet(
            sourceSetName = "iosMain",
            availableSourceSets = listOf("commonMain", "jvmMain"),
        )

        assertTrue(error.message().contains("iosMain"))
    }

    @Test
    fun missingKotlinSourceSetMessageListsAvailableSourceSets() {
        val error = PluginConfigurationError.MissingKotlinSourceSet(
            sourceSetName = "iosMain",
            availableSourceSets = listOf("commonMain", "jvmMain"),
        )

        assertTrue(error.message().contains("commonMain"))
        assertTrue(error.message().contains("jvmMain"))
    }

    @Test
    fun missingKotlinSourceSetMessageWorksWithSingleAvailableSourceSet() {
        val error = PluginConfigurationError.MissingKotlinSourceSet(
            sourceSetName = "iosMain",
            availableSourceSets = listOf("commonMain"),
        )

        assertTrue(error.message().contains("commonMain"))
    }

    @Test
    fun missingKotlinSourceSetHasNullCause() {
        val error = PluginConfigurationError.MissingKotlinSourceSet(
            sourceSetName = "iosMain",
            availableSourceSets = emptyList(),
        )

        assertNull(error.cause)
    }

    @Test
    fun missingKotlinSourceSetMessageWorksWithEmptyAvailableList() {
        val error = PluginConfigurationError.MissingKotlinSourceSet(
            sourceSetName = "iosMain",
            availableSourceSets = emptyList(),
        )

        assertTrue(error.message().isNotBlank())
        assertTrue(error.message().contains("iosMain"))
    }

    // -------- toGradleException --------

    @Test
    fun blankTargetSourceSetNameConvertsToGradleException() {
        val error = PluginConfigurationError.BlankTargetSourceSetName(index = 1)

        val exception = error.toGradleException()

        assertEquals(error.message(), exception.message)
    }

    @Test
    fun blankTargetNameConvertsToGradleException() {
        val error = PluginConfigurationError.BlankTargetName(index = 2)

        val exception = error.toGradleException()

        assertEquals(error.message(), exception.message)
    }

    @Test
    fun duplicateTargetSourceSetConvertsToGradleException() {
        val error = PluginConfigurationError.DuplicateTargetSourceSet(
            sourceSetName = "main",
            firstTargetName = "a",
            duplicateTargetName = "b",
        )

        val exception = error.toGradleException()

        assertEquals(error.message(), exception.message)
    }

    @Test
    fun missingKotlinSourceSetConvertsToGradleException() {
        val error = PluginConfigurationError.MissingKotlinSourceSet(
            sourceSetName = "iosMain",
            availableSourceSets = listOf("commonMain"),
        )

        val exception = error.toGradleException()

        assertEquals(error.message(), exception.message)
    }
}