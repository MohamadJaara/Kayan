package io.kayan.gradle

import arrow.core.getOrElse
import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidFlavorSourceGenerationSupportTest {
    @Test
    fun normalizesDistinctAndroidFlavorGenerations() {
        val generations = androidFlavorSourceGenerationsEither(
            listOf(" prod ", "dev", "prod", "fdroid"),
        ).getOrElse { throw it.toGradleException() }

        assertEquals(
            listOf("prod", "dev", "fdroid"),
            generations.map(AndroidFlavorSourceGeneration::flavorName),
        )
        assertEquals(
            listOf("generateKayanProdConfig", "generateKayanDevConfig", "generateKayanFdroidConfig"),
            generations.map(AndroidFlavorSourceGeneration::taskName),
        )
    }

    @Test
    fun rejectsBlankAndroidFlavorNames() {
        val error = assertFailsWith<GradleException> {
            androidFlavorSourceGenerationsEither(listOf("prod", "   "))
                .getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "Kayan android flavor source generation requires non-blank flavor names.",
            "index 1",
        )
    }

    @Test
    fun validatesConfiguredFlavorSourceSets() {
        val generations = androidFlavorSourceGenerationsEither(
            listOf("prod", "dev"),
        ).getOrElse { throw it.toGradleException() }

        val error = assertFailsWith<GradleException> {
            validateAndroidFlavorSourceSetsEither(
                configuredFlavors = generations,
                availableSourceSetNames = setOf("main", "prod"),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "could not find a Kotlin source set named 'dev'",
            "Available source sets: 'main', 'prod'.",
        )
    }
}
