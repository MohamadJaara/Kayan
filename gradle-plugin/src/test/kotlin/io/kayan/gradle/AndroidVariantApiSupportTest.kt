package io.kayan.gradle

import arrow.core.getOrElse
import io.kayan.assertMessageContains
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidVariantApiSupportTest {
    @Test
    fun registersConfiguredFlavorTasksWithAndroidVariantApi() {
        val project = ProjectBuilder.builder().build()
        val prodTask = project.tasks.register("generateKayanProdConfig", GenerateKayanConfigTask::class.java) { task ->
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/android/prod"))
        }
        val devTask = project.tasks.register("generateKayanDevConfig", GenerateKayanConfigTask::class.java) { task ->
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/android/dev"))
        }
        val generations = androidFlavorSourceGenerationsEither(listOf("prod", "dev"))
            .getOrElse { throw it.toGradleException() }
        val androidComponents = FakeAndroidComponentsExtension(
            variants = listOf(
                FakeVariant("prodDebug", listOf(FakeVariantFlavor("environment", "prod"))),
                FakeVariant("devDebug", listOf(FakeVariantFlavor("environment", "dev"))),
                FakeVariant("freeDebug", listOf(FakeVariantFlavor("distribution", "free"))),
            ),
        )

        registerAndroidGeneratedSourcesEither(
            androidComponentsExtension = androidComponents,
            defaultGenerateTask = null,
            configuredGenerations = generations,
            generationTasksByFlavor = mapOf(
                "prod" to prodTask,
                "dev" to devTask,
            ),
        ).getOrElse { throw it.toGradleException() }

        assertEquals(
            listOf("generateKayanProdConfig"),
            androidComponents.variants[0].registeredTaskNames(),
        )
        assertEquals(
            listOf("generateKayanDevConfig"),
            androidComponents.variants[1].registeredTaskNames(),
        )
        assertEquals(
            emptyList(),
            androidComponents.variants[2].registeredTaskNames(),
        )
    }

    @Test
    fun registersDefaultTaskForAllAndroidVariantsWhenNoFlavorSpecificGenerationIsConfigured() {
        val project = ProjectBuilder.builder().build()
        val defaultTask = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java) { task ->
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/kotlin"))
        }
        val androidComponents = FakeAndroidComponentsExtension(
            variants = listOf(
                FakeVariant("debug", emptyList()),
                FakeVariant("release", emptyList()),
            ),
        )

        registerAndroidGeneratedSourcesEither(
            androidComponentsExtension = androidComponents,
            defaultGenerateTask = defaultTask,
            configuredGenerations = emptyList(),
            generationTasksByFlavor = emptyMap(),
        ).getOrElse { throw it.toGradleException() }

        assertEquals(
            listOf("generateKayanConfig"),
            androidComponents.variants[0].registeredTaskNames(),
        )
        assertEquals(
            listOf("generateKayanConfig"),
            androidComponents.variants[1].registeredTaskNames(),
        )
    }

    @Test
    fun failsWhenAndroidVariantDoesNotExposeKotlinSourceRegistration() {
        val project = ProjectBuilder.builder().build()
        val defaultTask = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java) { task ->
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/kotlin"))
        }
        val androidComponents = FakeAndroidComponentsExtension(
            variants = listOf(
                FakeVariant("debug", emptyList(), variantSources = FakeVariantSourcesWithoutKotlin()),
            ),
        )

        val error = assertFailsWith<GradleException> {
            registerAndroidGeneratedSourcesEither(
                androidComponentsExtension = androidComponents,
                defaultGenerateTask = defaultTask,
                configuredGenerations = emptyList(),
                generationTasksByFlavor = emptyMap(),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "could not register Android generated sources through the Android Variant API",
            "variant 'debug'",
            "sources.kotlin",
        )
    }

    private class FakeAndroidComponentsExtension(
        val variants: List<FakeVariant>,
    ) {
        fun selector(): FakeVariantSelector = FakeVariantSelector()

        fun onVariants(@Suppress("UNUSED_PARAMETER") selector: FakeVariantSelector, action: Action<FakeVariant>) {
            variants.forEach(action::execute)
        }
    }

    private class FakeVariantSelector {
        fun all(): FakeVariantSelector = this
    }

    private class FakeVariant(
        private val name: String,
        private val productFlavors: List<FakeVariantFlavor>,
        private val variantSources: Any = FakeVariantSources(),
    ) {
        fun getName(): String = name

        fun getProductFlavors(): List<FakeVariantFlavor> = productFlavors

        fun getSources(): Any = variantSources

        fun registeredTaskNames(): List<String> =
            (variantSources as? FakeVariantSources)?.registeredTaskNames().orEmpty()
    }

    private class FakeVariantFlavor(
        private val first: String,
        private val second: String,
    ) {
        fun getFirst(): String = first

        fun getSecond(): String = second
    }

    private open class FakeVariantSources {
        private val kotlinSources: FakeKotlinSources = FakeKotlinSources()

        fun getKotlin(): FakeKotlinSources = kotlinSources

        fun registeredTaskNames(): List<String> = kotlinSources.registeredTaskNames
    }

    private class FakeVariantSourcesWithoutKotlin

    private class FakeKotlinSources {
        val registeredTaskNames: MutableList<String> = mutableListOf()

        @Suppress("UNUSED_PARAMETER")
        fun addGeneratedSourceDirectory(
            taskProvider: TaskProvider<out Task>,
            accessor: (GenerateKayanConfigTask) -> DirectoryProperty,
        ) {
            registeredTaskNames += taskProvider.name
            accessor(taskProvider.get() as GenerateKayanConfigTask)
        }
    }
}
