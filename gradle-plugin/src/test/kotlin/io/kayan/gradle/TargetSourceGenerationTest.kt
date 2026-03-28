package io.kayan.gradle

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalKayanGenerationApi::class)
class TargetSourceGenerationTest {

    // -------- targetSourceGenerationsEither --------

    @Test
    fun returnsEmptyListWhenNoMappingsConfigured() {
        val result = targetSourceGenerationsEither(emptyList())

        assertIs<Either.Right<List<TargetSourceGeneration>>>(result)
        assertEquals(emptyList(), result.value)
    }

    @Test
    fun buildsCorrectGenerationsForValidMappings() {
        val mappings = listOf(
            KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
            KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = "ios"),
        )

        val result = targetSourceGenerationsEither(mappings)

        assertIs<Either.Right<List<TargetSourceGeneration>>>(result)
        assertEquals(
            listOf(
                TargetSourceGeneration(
                    sourceSetName = "jvmMain",
                    targetName = "jvm",
                    taskName = "generateKayanJvmMainConfig",
                ),
                TargetSourceGeneration(
                    sourceSetName = "iosMain",
                    targetName = "ios",
                    taskName = "generateKayanIosMainConfig",
                ),
            ),
            result.value,
        )
    }

    @Test
    fun trimsWhitespaceFromSourceSetAndTargetNames() {
        val mappings = listOf(
            KayanTargetSourceSetMapping(sourceSetName = "  jvmMain  ", targetName = "  jvm  "),
        )

        val result = targetSourceGenerationsEither(mappings)

        assertIs<Either.Right<List<TargetSourceGeneration>>>(result)
        val generation = result.value.single()
        assertEquals("jvmMain", generation.sourceSetName)
        assertEquals("jvm", generation.targetName)
    }

    @Test
    fun raisesBlankTargetSourceSetNameForEmptySourceSetName() {
        val mappings = listOf(
            KayanTargetSourceSetMapping(sourceSetName = "", targetName = "jvm"),
        )

        val result = targetSourceGenerationsEither(mappings)

        assertIs<Either.Left<PluginConfigurationError>>(result)
        val error = assertIs<PluginConfigurationError.BlankTargetSourceSetName>(result.value)
        assertEquals(0, error.index)
    }

    @Test
    fun raisesBlankTargetSourceSetNameForWhitespaceOnlySourceSetName() {
        val mappings = listOf(
            KayanTargetSourceSetMapping(sourceSetName = "valid", targetName = "t1"),
            KayanTargetSourceSetMapping(sourceSetName = "   ", targetName = "t2"),
        )

        val result = targetSourceGenerationsEither(mappings)

        assertIs<Either.Left<PluginConfigurationError>>(result)
        val error = assertIs<PluginConfigurationError.BlankTargetSourceSetName>(result.value)
        assertEquals(1, error.index)
    }

    @Test
    fun raisesBlankTargetNameForEmptyTargetName() {
        val mappings = listOf(
            KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = ""),
        )

        val result = targetSourceGenerationsEither(mappings)

        assertIs<Either.Left<PluginConfigurationError>>(result)
        val error = assertIs<PluginConfigurationError.BlankTargetName>(result.value)
        assertEquals(0, error.index)
    }

    @Test
    fun raisesBlankTargetNameForWhitespaceOnlyTargetName() {
        val mappings = listOf(
            KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "\t"),
        )

        val result = targetSourceGenerationsEither(mappings)

        assertIs<Either.Left<PluginConfigurationError>>(result)
        val error = assertIs<PluginConfigurationError.BlankTargetName>(result.value)
        assertEquals(0, error.index)
    }

    @Test
    fun deduplicatesMappingsWhenSameSourceSetMapsToSameTarget() {
        val mappings = listOf(
            KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
            KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
        )

        val result = targetSourceGenerationsEither(mappings)

        assertIs<Either.Right<List<TargetSourceGeneration>>>(result)
        assertEquals(1, result.value.size)
        assertEquals("jvmMain", result.value.single().sourceSetName)
    }

    @Test
    fun raisesDuplicateTargetSourceSetWhenSameSourceSetMapsToDifferentTargets() {
        val mappings = listOf(
            KayanTargetSourceSetMapping(sourceSetName = "sharedMain", targetName = "ios"),
            KayanTargetSourceSetMapping(sourceSetName = "sharedMain", targetName = "android"),
        )

        val result = targetSourceGenerationsEither(mappings)

        assertIs<Either.Left<PluginConfigurationError>>(result)
        val error = assertIs<PluginConfigurationError.DuplicateTargetSourceSet>(result.value)
        assertEquals("sharedMain", error.sourceSetName)
        assertEquals("ios", error.firstTargetName)
        assertEquals("android", error.duplicateTargetName)
    }

    @Test
    fun generatesCorrectTaskNameForMultiWordSourceSetName() {
        val mappings = listOf(
            KayanTargetSourceSetMapping(sourceSetName = "androidMain", targetName = "android"),
        )

        val result = targetSourceGenerationsEither(mappings)

        assertIs<Either.Right<List<TargetSourceGeneration>>>(result)
        assertEquals("generateKayanAndroidMainConfig", result.value.single().taskName)
    }

    // -------- validateConfiguredSourceSetsEither --------

    @Test
    fun returnsUnitWhenAllConfiguredSourceSetsAreAvailable() {
        val available = setOf("commonMain", "jvmMain", "iosMain")
        val generations = listOf(
            TargetSourceGeneration(sourceSetName = "jvmMain", targetName = "jvm", taskName = "generateKayanJvmMainConfig"),
            TargetSourceGeneration(sourceSetName = "iosMain", targetName = "ios", taskName = "generateKayanIosMainConfig"),
        )

        val result = validateConfiguredSourceSetsEither(
            availableSourceSets = available,
            configuredGenerations = generations,
        )

        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun returnsUnitWhenConfiguredGenerationsIsEmpty() {
        val result = validateConfiguredSourceSetsEither(
            availableSourceSets = setOf("commonMain"),
            configuredGenerations = emptyList(),
        )

        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun raisesMissingKotlinSourceSetForUnknownSourceSet() {
        val available = setOf("commonMain", "jvmMain")
        val generations = listOf(
            TargetSourceGeneration(
                sourceSetName = "iosMain",
                targetName = "ios",
                taskName = "generateKayanIosMainConfig",
            ),
        )

        val result = validateConfiguredSourceSetsEither(
            availableSourceSets = available,
            configuredGenerations = generations,
        )

        assertIs<Either.Left<PluginConfigurationError>>(result)
        val error = assertIs<PluginConfigurationError.MissingKotlinSourceSet>(result.value)
        assertEquals("iosMain", error.sourceSetName)
    }

    @Test
    fun sortesAvailableSourceSetsInMissingKotlinSourceSetError() {
        val available = setOf("jvmMain", "androidMain", "commonMain")
        val generations = listOf(
            TargetSourceGeneration(
                sourceSetName = "nonexistentMain",
                targetName = "target",
                taskName = "generateKayanNonexistentMainConfig",
            ),
        )

        val result = validateConfiguredSourceSetsEither(
            availableSourceSets = available,
            configuredGenerations = generations,
        )

        assertIs<Either.Left<PluginConfigurationError>>(result)
        val error = assertIs<PluginConfigurationError.MissingKotlinSourceSet>(result.value)
        assertEquals(listOf("androidMain", "commonMain", "jvmMain"), error.availableSourceSets)
    }

    @Test
    fun reportsFirstMissingSourceSetWhenMultipleAreMissing() {
        val available = setOf("commonMain")
        val generations = listOf(
            TargetSourceGeneration(sourceSetName = "jvmMain", targetName = "jvm", taskName = "generateKayanJvmMainConfig"),
            TargetSourceGeneration(sourceSetName = "iosMain", targetName = "ios", taskName = "generateKayanIosMainConfig"),
        )

        val result = validateConfiguredSourceSetsEither(
            availableSourceSets = available,
            configuredGenerations = generations,
        )

        assertIs<Either.Left<PluginConfigurationError>>(result)
        val error = assertIs<PluginConfigurationError.MissingKotlinSourceSet>(result.value)
        assertEquals("jvmMain", error.sourceSetName)
    }
}