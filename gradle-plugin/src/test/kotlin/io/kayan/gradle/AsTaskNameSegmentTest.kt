package io.kayan.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [String.asTaskNameSegment], which was promoted from private to internal in this PR
 * so it can be shared by both Android flavor and target source generation task name builders.
 */
class AsTaskNameSegmentTest {

    @Test
    fun simpleAlphanumericStringIsCapitalized() {
        assertEquals("Jvm", "jvm".asTaskNameSegment())
    }

    @Test
    fun alreadyCapitalizedStringIsUnchanged() {
        assertEquals("Jvm", "Jvm".asTaskNameSegment())
    }

    @Test
    fun camelCaseSourceSetNameProducesCapitalizedSegments() {
        assertEquals("JvmMain", "jvmMain".asTaskNameSegment())
    }

    @Test
    fun hyphenatedNameJoinsSegmentsWithCapitalization() {
        assertEquals("IosShared", "ios-shared".asTaskNameSegment())
    }

    @Test
    fun underscoreDelimitedNameJoinsSegments() {
        assertEquals("AndroidMain", "android_main".asTaskNameSegment())
    }

    @Test
    fun mixedDelimitersAreAllTreatedAsSeparators() {
        assertEquals("AppleIosMain", "apple-ios_main".asTaskNameSegment())
    }

    @Test
    fun allUpperCaseStringIsPreservedAsIs() {
        assertEquals("JVMMAIN", "JVMMAIN".asTaskNameSegment())
    }

    @Test
    fun numbersAreRetainedAsPartOfSegment() {
        assertEquals("Wasm32Main", "wasm32Main".asTaskNameSegment())
    }

    @Test
    fun leadingAndTrailingDelimitersAreIgnored() {
        assertEquals("Jvm", "-jvm-".asTaskNameSegment())
    }

    @Test
    fun consecutiveDelimitersProduceSingleCapitalizedSegment() {
        assertEquals("JvmMain", "jvm--main".asTaskNameSegment())
    }
}