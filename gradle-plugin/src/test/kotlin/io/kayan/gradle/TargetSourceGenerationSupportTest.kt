package io.kayan.gradle

import arrow.core.getOrElse
import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalKayanGenerationApi::class)
class TargetSourceGenerationSupportTest {
    @Test
    fun normalizesDistinctTargetSourceGenerations() {
        val generations = targetSourceGenerationsEither(
            listOf(
                KayanTargetSourceSetMapping(sourceSetName = " iosMain ", targetName = " ios "),
                KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "desktop"),
                KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = "ios"),
            ),
        ).getOrElse { throw it.toGradleException() }

        assertEquals(
            listOf("iosMain", "jvmMain"),
            generations.map(TargetSourceGeneration::sourceSetName),
        )
        assertEquals(
            listOf("ios", "desktop"),
            generations.map(TargetSourceGeneration::targetName),
        )
        assertEquals(
            listOf("generateKayanIosMainConfig", "generateKayanJvmMainConfig"),
            generations.map(TargetSourceGeneration::taskName),
        )
    }

    @Test
    fun rejectsBlankTargetSourceSetNamesWithEntryDetails() {
        val error = assertFailsWith<GradleException> {
            targetSourceGenerationsEither(
                listOf(
                    KayanTargetSourceSetMapping(sourceSetName = "   ", targetName = "ios"),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "Kayan target source generation requires non-blank Kotlin source set names.",
            "index 0",
            "field 'sourceSetName'",
            "configured value '   '",
        )
    }

    @Test
    fun rejectsBlankTargetNamesWithEntryDetails() {
        val error = assertFailsWith<GradleException> {
            targetSourceGenerationsEither(
                listOf(
                    KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = ""),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "Kayan target source generation requires non-blank target names.",
            "index 0",
            "field 'targetName'",
            "configured value ''",
        )
    }

    @Test
    fun rejectsDuplicateSourceSetMappingsWithDifferentTargets() {
        val error = assertFailsWith<GradleException> {
            targetSourceGenerationsEither(
                listOf(
                    KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = "ios"),
                    KayanTargetSourceSetMapping(sourceSetName = " iosMain ", targetName = "apple"),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "source set 'iosMain'",
            "'ios'",
            "'apple'",
            "only resolve one target",
        )
    }

    @Test
    fun validatesConfiguredSourceSetNamesAgainstAvailableSourceSets() {
        val error = assertFailsWith<GradleException> {
            validateConfiguredSourceSetsEither(
                availableSourceSets = setOf("commonMain", "jvmMain"),
                configuredGenerations = listOf(
                    TargetSourceGeneration(
                        sourceSetName = "appleMain",
                        targetName = "apple",
                        taskName = "generateKayanAppleMainConfig",
                    ),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "could not find Kotlin source set 'appleMain'",
            "'commonMain'",
            "'jvmMain'",
        )
    }
}
