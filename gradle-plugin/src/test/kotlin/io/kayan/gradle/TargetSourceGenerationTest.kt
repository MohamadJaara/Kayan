package io.kayan.gradle

import arrow.core.getOrElse
import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalKayanGenerationApi::class)
class TargetSourceGenerationTest {

    // ── targetSourceGenerationsEither ────────────────────────────────────────

    @Test
    fun emptyMappingsReturnEmptyGenerations() {
        val generations = targetSourceGenerationsEither(emptyList())
            .getOrElse { throw it.toGradleException() }

        assertTrue(generations.isEmpty())
    }

    @Test
    fun validMappingsReturnGenerationsWithCorrectFields() {
        val generations = targetSourceGenerationsEither(
            listOf(
                KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
                KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = "ios"),
            ),
        ).getOrElse { throw it.toGradleException() }

        assertEquals(2, generations.size)
        assertEquals("jvmMain", generations[0].sourceSetName)
        assertEquals("jvm", generations[0].targetName)
        assertEquals("iosMain", generations[1].sourceSetName)
        assertEquals("ios", generations[1].targetName)
    }

    @Test
    fun taskNameDerivedFromSourceSetNameViaAsTaskNameSegment() {
        val generations = targetSourceGenerationsEither(
            listOf(
                KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
                KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = "ios"),
                KayanTargetSourceSetMapping(sourceSetName = "androidMain", targetName = "android"),
            ),
        ).getOrElse { throw it.toGradleException() }

        assertEquals("generateKayanJvmMainConfig", generations[0].taskName)
        assertEquals("generateKayanIosMainConfig", generations[1].taskName)
        assertEquals("generateKayanAndroidMainConfig", generations[2].taskName)
    }

    @Test
    fun trimmingAppliedToSourceSetAndTargetNames() {
        val generations = targetSourceGenerationsEither(
            listOf(
                KayanTargetSourceSetMapping(sourceSetName = "  jvmMain  ", targetName = "  jvm  "),
            ),
        ).getOrElse { throw it.toGradleException() }

        assertEquals(1, generations.size)
        assertEquals("jvmMain", generations[0].sourceSetName)
        assertEquals("jvm", generations[0].targetName)
    }

    @Test
    fun blankSourceSetNameAtFirstIndexRaisesError() {
        val error = assertFailsWith<GradleException> {
            targetSourceGenerationsEither(
                listOf(
                    KayanTargetSourceSetMapping(sourceSetName = "", targetName = "jvm"),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "non-blank Kotlin source set names",
            "index 0",
        )
    }

    @Test
    fun whitespaceOnlySourceSetNameRaisesErrorAtCorrectIndex() {
        val error = assertFailsWith<GradleException> {
            targetSourceGenerationsEither(
                listOf(
                    KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
                    KayanTargetSourceSetMapping(sourceSetName = "   ", targetName = "ios"),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "non-blank Kotlin source set names",
            "index 1",
        )
    }

    @Test
    fun blankTargetNameAtFirstIndexRaisesError() {
        val error = assertFailsWith<GradleException> {
            targetSourceGenerationsEither(
                listOf(
                    KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = ""),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "non-blank target names",
            "index 0",
        )
    }

    @Test
    fun whitespaceOnlyTargetNameRaisesErrorAtCorrectIndex() {
        val error = assertFailsWith<GradleException> {
            targetSourceGenerationsEither(
                listOf(
                    KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
                    KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = "  "),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "non-blank target names",
            "index 1",
        )
    }

    @Test
    fun duplicateSourceSetNameWithDifferentTargetRaisesDuplicateError() {
        val error = assertFailsWith<GradleException> {
            targetSourceGenerationsEither(
                listOf(
                    KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
                    KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "desktop"),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "'jvmMain'",
            "'jvm'",
            "'desktop'",
            "more than once",
        )
    }

    @Test
    fun duplicateSourceSetNameWithSameTargetIsDeduplicatedWithoutError() {
        val generations = targetSourceGenerationsEither(
            listOf(
                KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
                KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
            ),
        ).getOrElse { throw it.toGradleException() }

        // Duplicate with same target is silently deduplicated
        assertEquals(1, generations.size)
        assertEquals("jvmMain", generations[0].sourceSetName)
        assertEquals("jvm", generations[0].targetName)
    }

    @Test
    fun insertionOrderPreservedInOutput() {
        val generations = targetSourceGenerationsEither(
            listOf(
                KayanTargetSourceSetMapping(sourceSetName = "iosMain", targetName = "ios"),
                KayanTargetSourceSetMapping(sourceSetName = "androidMain", targetName = "android"),
                KayanTargetSourceSetMapping(sourceSetName = "jvmMain", targetName = "jvm"),
            ),
        ).getOrElse { throw it.toGradleException() }

        assertEquals(listOf("iosMain", "androidMain", "jvmMain"), generations.map { it.sourceSetName })
    }

    // ── validateConfiguredSourceSetsEither ───────────────────────────────────

    @Test
    fun allConfiguredSourceSetsAvailableReturnsRight() {
        val generations = listOf(
            TargetSourceGeneration(sourceSetName = "jvmMain", targetName = "jvm", taskName = "generateKayanJvmMainConfig"),
            TargetSourceGeneration(sourceSetName = "iosMain", targetName = "ios", taskName = "generateKayanIosMainConfig"),
        )

        // Should not throw
        validateConfiguredSourceSetsEither(
            availableSourceSets = setOf("commonMain", "jvmMain", "iosMain"),
            configuredGenerations = generations,
        ).getOrElse { throw it.toGradleException() }
    }

    @Test
    fun emptyGenerationsAlwaysReturnsRight() {
        validateConfiguredSourceSetsEither(
            availableSourceSets = emptySet(),
            configuredGenerations = emptyList(),
        ).getOrElse { throw it.toGradleException() }
    }

    @Test
    fun missingSourceSetReturnsError() {
        val error = assertFailsWith<GradleException> {
            validateConfiguredSourceSetsEither(
                availableSourceSets = setOf("commonMain", "jvmMain"),
                configuredGenerations = listOf(
                    TargetSourceGeneration(
                        sourceSetName = "iosMain",
                        targetName = "ios",
                        taskName = "generateKayanIosMainConfig",
                    ),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(error, "'iosMain'")
    }

    @Test
    fun missingSourceSetErrorListsAvailableSortedAlphabetically() {
        val error = assertFailsWith<GradleException> {
            validateConfiguredSourceSetsEither(
                availableSourceSets = setOf("jvmMain", "commonMain", "androidMain"),
                configuredGenerations = listOf(
                    TargetSourceGeneration(
                        sourceSetName = "missingMain",
                        targetName = "missing",
                        taskName = "generateKayanMissingMainConfig",
                    ),
                ),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "'missingMain'",
            "'androidMain'",
            "'commonMain'",
            "'jvmMain'",
        )
        // Verify alphabetical ordering in the message
        val message = error.message ?: ""
        val androidIndex = message.indexOf("'androidMain'")
        val commonIndex = message.indexOf("'commonMain'")
        val jvmIndex = message.indexOf("'jvmMain'")
        assertTrue(androidIndex < commonIndex, "androidMain should appear before commonMain")
        assertTrue(commonIndex < jvmIndex, "commonMain should appear before jvmMain")
    }

    @Test
    fun firstMissingSourceSetIsReportedWhenMultipleAreMissing() {
        val error = assertFailsWith<GradleException> {
            validateConfiguredSourceSetsEither(
                availableSourceSets = setOf("commonMain"),
                configuredGenerations = listOf(
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
            ).getOrElse { throw it.toGradleException() }
        }

        // The first missing source set (jvmMain) should be reported
        assertMessageContains(error, "'jvmMain'")
    }

    // ── asTaskNameSegment ────────────────────────────────────────────────────

    @Test
    fun asTaskNameSegmentCapitalizesFirstLetterOfEachSegment() {
        assertEquals("JvmMain", "jvmMain".asTaskNameSegment())
        assertEquals("IosMain", "iosMain".asTaskNameSegment())
        assertEquals("AndroidMain", "androidMain".asTaskNameSegment())
    }

    @Test
    fun asTaskNameSegmentHandlesAlreadyCapitalizedInput() {
        assertEquals("JvmMain", "JvmMain".asTaskNameSegment())
        assertEquals("IOS", "IOS".asTaskNameSegment())
    }

    @Test
    fun asTaskNameSegmentSplitsOnNonAlphanumericSeparators() {
        assertEquals("IosShared", "ios-shared".asTaskNameSegment())
        assertEquals("MySourceSet", "my_source_set".asTaskNameSegment())
        assertEquals("FooBarBaz", "foo.bar.baz".asTaskNameSegment())
    }

    @Test
    fun asTaskNameSegmentFiltersBlankSegments() {
        assertEquals("FooBar", "foo--bar".asTaskNameSegment())
        assertEquals("Foo", "foo".asTaskNameSegment())
    }

    @Test
    fun asTaskNameSegmentProducesExpectedTaskNameForConventionalSourceSets() {
        // These match the task names generated in targetSourceGenerationsEither
        assertEquals("JvmMain", "jvmMain".asTaskNameSegment())
        assertEquals("IosMain", "iosMain".asTaskNameSegment())
        assertEquals("AndroidMain", "androidMain".asTaskNameSegment())
        assertEquals("JsMain", "jsMain".asTaskNameSegment())
        assertEquals("WasmJsMain", "wasmJsMain".asTaskNameSegment())
    }
}