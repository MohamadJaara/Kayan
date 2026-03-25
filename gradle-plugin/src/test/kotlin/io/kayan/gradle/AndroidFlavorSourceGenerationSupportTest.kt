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

    @Test
    fun rejectsConfiguredFlavorsAcrossMultipleAndroidDimensions() {
        val generations = androidFlavorSourceGenerationsEither(
            listOf("prod", "fdroid"),
        ).getOrElse { throw it.toGradleException() }

        val error = assertFailsWith<GradleException> {
            validateAndroidFlavorDimensionsEither(
                androidExtension = FakeAndroidExtension(
                    productFlavors = listOf(
                        FakeProductFlavor(name = "prod", dimension = "environment"),
                        FakeProductFlavor(name = "dev", dimension = "environment"),
                        FakeProductFlavor(name = "fdroid", dimension = "distribution"),
                    ),
                ),
                configuredFlavors = generations,
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "supports flavors from a single Android flavor dimension",
            "'prod'",
            "'fdroid'",
            "'distribution'",
            "'environment'",
        )
    }

    @Test
    fun allowsConfiguredFlavorsFromSingleAndroidDimension() {
        val generations = androidFlavorSourceGenerationsEither(
            listOf("prod", "dev"),
        ).getOrElse { throw it.toGradleException() }

        validateAndroidFlavorDimensionsEither(
            androidExtension = FakeAndroidExtension(
                productFlavors = listOf(
                    FakeProductFlavor(name = "prod", dimension = "environment"),
                    FakeProductFlavor(name = "dev", dimension = "environment"),
                    FakeProductFlavor(name = "fdroid", dimension = "distribution"),
                ),
            ),
            configuredFlavors = generations,
        ).getOrElse { throw it.toGradleException() }
    }

    private class FakeAndroidExtension(
        private val productFlavors: List<FakeProductFlavor>,
    ) {
        fun getProductFlavors(): List<FakeProductFlavor> = productFlavors
    }

    private class FakeProductFlavor(
        private val name: String,
        private val dimension: String?,
    ) {
        fun getName(): String = name

        fun getDimension(): String? = dimension
    }
}
