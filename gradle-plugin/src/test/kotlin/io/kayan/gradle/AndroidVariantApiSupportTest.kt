package io.kayan.gradle

import arrow.core.getOrElse
import arrow.core.left
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
    fun failsWhenAndroidComponentsExtensionIsMissing() {
        val error = assertFailsWith<GradleException> {
            registerAndroidGeneratedSourcesEither(
                androidComponentsExtension = null,
                defaultGenerateTask = null,
                configuredGenerations = emptyList(),
                generationTasksByFlavor = emptyMap(),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "could not register Android generated sources through the Android Variant API",
            "androidComponents",
            "was not found",
        )
    }

    @Test
    fun failsWhenAndroidComponentsSelectorIsMissing() {
        val error = assertFailsWith<GradleException> {
            registerAndroidGeneratedSourcesEither(
                androidComponentsExtension = FakeAndroidComponentsWithoutSelector(),
                defaultGenerateTask = null,
                configuredGenerations = emptyList(),
                generationTasksByFlavor = emptyMap(),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(error, "does not expose selector()")
    }

    @Test
    fun failsWhenAndroidComponentsSelectorDoesNotExposeAll() {
        val error = assertFailsWith<GradleException> {
            registerAndroidGeneratedSourcesEither(
                androidComponentsExtension = FakeAndroidComponentsWithSelectorWithoutAll(),
                defaultGenerateTask = null,
                configuredGenerations = emptyList(),
                generationTasksByFlavor = emptyMap(),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(error, "selector does not expose all()")
    }

    @Test
    fun failsWhenAndroidComponentsDoesNotExposeOnVariants() {
        val error = assertFailsWith<GradleException> {
            registerAndroidGeneratedSourcesEither(
                androidComponentsExtension = FakeAndroidComponentsWithoutOnVariants(),
                defaultGenerateTask = null,
                configuredGenerations = emptyList(),
                generationTasksByFlavor = emptyMap(),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(error, "does not expose onVariants(selector, action)")
    }

    @Test
    fun failsWhenOnVariantsInvocationThrows() {
        val error = assertFailsWith<GradleException> {
            registerAndroidGeneratedSourcesEither(
                androidComponentsExtension = FakeAndroidComponentsThrowingOnVariants(),
                defaultGenerateTask = null,
                configuredGenerations = emptyList(),
                generationTasksByFlavor = emptyMap(),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(error, "Failed to register generated Kotlin sources with onVariants")
    }

    @Test
    fun surfacesGenerationResolverErrorsFromVariantCallbacks() {
        val error = assertFailsWith<GradleException> {
            registerAndroidGeneratedSourcesEither(
                androidComponentsExtension = FakeAndroidComponentsExtension(
                    variants = listOf(FakeVariant("prodDebug", listOf(FakeVariantFlavor("environment", "prod")))),
                ),
                generationResolver = AndroidVariantGenerationResolver {
                    PluginConfigurationError.MissingAndroidProductFlavor(
                        flavorName = "prod",
                        availableFlavors = listOf("dev"),
                    ).left()
                },
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(
            error,
            "could not find an Android product flavor named 'prod'",
            "Available product flavors: 'dev'.",
        )
    }

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
            (androidComponents.variants[0] as FakeVariant).registeredTaskNames(),
        )
        assertEquals(
            listOf("generateKayanDevConfig"),
            (androidComponents.variants[1] as FakeVariant).registeredTaskNames(),
        )
        assertEquals(
            emptyList(),
            (androidComponents.variants[2] as FakeVariant).registeredTaskNames(),
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
            (androidComponents.variants[0] as FakeVariant).registeredTaskNames(),
        )
        assertEquals(
            listOf("generateKayanConfig"),
            (androidComponents.variants[1] as FakeVariant).registeredTaskNames(),
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

    @Test
    fun failsWhenAndroidVariantDoesNotExposeSources() {
        val project = ProjectBuilder.builder().build()
        val defaultTask = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java) { task ->
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/kotlin"))
        }
        val androidComponents = FakeAndroidComponentsExtension(
            variants = listOf(FakeVariantWithoutSources("")),
        )

        val error = assertFailsWith<GradleException> {
            registerAndroidGeneratedSourcesEither(
                androidComponentsExtension = androidComponents,
                defaultGenerateTask = defaultTask,
                configuredGenerations = emptyList(),
                generationTasksByFlavor = emptyMap(),
            ).getOrElse { throw it.toGradleException() }
        }

        assertMessageContains(error, "variant '<unknown>'", "does not expose getSources()")
    }

    @Test
    fun failsWhenKotlinSourcesDoNotExposeGeneratedDirectoryRegistration() {
        val project = ProjectBuilder.builder().build()
        val defaultTask = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java) { task ->
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/kotlin"))
        }
        val androidComponents = FakeAndroidComponentsExtension(
            variants = listOf(
                FakeVariant("debug", emptyList(), variantSources = FakeVariantSourcesWithoutGeneratedDirectory()),
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
            "variant 'debug'",
            "does not expose kotlin.addGeneratedSourceDirectory(task, accessor)",
        )
    }

    @Test
    fun failsWhenGeneratedDirectoryRegistrationThrows() {
        val project = ProjectBuilder.builder().build()
        val defaultTask = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java) { task ->
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/kotlin"))
        }
        val androidComponents = FakeAndroidComponentsExtension(
            variants = listOf(
                FakeVariant("debug", emptyList(), variantSources = FakeVariantSourcesWithThrowingKotlin()),
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

        assertMessageContains(error, "Failed to register generated Kotlin sources for Android variant 'debug'")
    }

    @Test
    fun matchesVariantFlavorNamesFromFallbackProperties() {
        val project = ProjectBuilder.builder().build()
        val prodTask = project.tasks.register("generateKayanProdConfig", GenerateKayanConfigTask::class.java) { task ->
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/android/prod"))
        }
        val fallbackTask = project.tasks.register(
            "generateKayanFallbackConfig",
            GenerateKayanConfigTask::class.java,
        ) { task ->
            task.outputDir.set(project.layout.buildDirectory.dir("generated/kayan/android/fallback"))
        }
        val generations = androidFlavorSourceGenerationsEither(listOf("prod", "fallback"))
            .getOrElse { throw it.toGradleException() }
        val androidComponents = FakeAndroidComponentsExtension(
            variants = listOf(
                FakeVariant("prodDebug", listOf(FakeNamedFlavor("prod"))),
                FakeVariant("fallbackDebug", listOf(FakeFlavorNameFlavor("fallback"))),
            ),
        )

        registerAndroidGeneratedSourcesEither(
            androidComponentsExtension = androidComponents,
            defaultGenerateTask = null,
            configuredGenerations = generations,
            generationTasksByFlavor = mapOf(
                "prod" to prodTask,
                "fallback" to fallbackTask,
            ),
        ).getOrElse { throw it.toGradleException() }

        assertEquals(
            listOf("generateKayanProdConfig"),
            (androidComponents.variants[0] as FakeVariant).registeredTaskNames(),
        )
        assertEquals(
            listOf("generateKayanFallbackConfig"),
            (androidComponents.variants[1] as FakeVariant).registeredTaskNames(),
        )
    }

    private class FakeAndroidComponentsExtension(
        val variants: List<Any>,
    ) {
        fun selector(): FakeVariantSelector = FakeVariantSelector()

        fun onVariants(@Suppress("UNUSED_PARAMETER") selector: FakeVariantSelector, action: Action<Any>) {
            variants.forEach(action::execute)
        }
    }

    private class FakeAndroidComponentsWithoutSelector

    private class FakeAndroidComponentsWithSelectorWithoutAll {
        fun selector(): FakeVariantSelectorWithoutAll = FakeVariantSelectorWithoutAll()
    }

    private class FakeAndroidComponentsWithoutOnVariants {
        fun selector(): FakeVariantSelector = FakeVariantSelector()
    }

    private class FakeAndroidComponentsThrowingOnVariants {
        fun selector(): FakeVariantSelector = FakeVariantSelector()

        fun onVariants(
            @Suppress("UNUSED_PARAMETER") selector: FakeVariantSelector,
            @Suppress("UNUSED_PARAMETER") action: Action<Any>,
        ) {
            error("boom")
        }
    }

    private class FakeVariantSelector {
        fun all(): FakeVariantSelector = this
    }

    private class FakeVariantSelectorWithoutAll

    private class FakeVariant(
        private val name: String,
        private val productFlavors: List<Any>,
        private val variantSources: Any = FakeVariantSources(),
    ) {
        fun getName(): String = name

        fun getProductFlavors(): List<Any> = productFlavors

        fun getSources(): Any = variantSources

        fun registeredTaskNames(): List<String> =
            (variantSources as? FakeVariantSources)?.registeredTaskNames().orEmpty()
    }

    private class FakeVariantWithoutSources(
        private val name: String,
    ) {
        fun getName(): String = name
    }

    private class FakeVariantFlavor(
        private val first: String,
        private val second: String,
    ) {
        fun getFirst(): String = first

        fun getSecond(): String = second
    }

    private class FakeNamedFlavor(
        private val name: String,
    ) {
        fun getName(): String = name
    }

    private class FakeFlavorNameFlavor(
        private val flavorName: String,
    ) {
        fun getFlavorName(): String = flavorName
    }

    private open class FakeVariantSources {
        private val kotlinSources: FakeKotlinSources = FakeKotlinSources()

        fun getKotlin(): FakeKotlinSources = kotlinSources

        fun registeredTaskNames(): List<String> = kotlinSources.registeredTaskNames
    }

    private class FakeVariantSourcesWithoutKotlin

    private class FakeVariantSourcesWithoutGeneratedDirectory {
        fun getKotlin(): FakeKotlinSourcesWithoutGeneratedDirectory = FakeKotlinSourcesWithoutGeneratedDirectory()
    }

    private class FakeVariantSourcesWithThrowingKotlin {
        fun getKotlin(): ThrowingFakeKotlinSources = ThrowingFakeKotlinSources()
    }

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

    private class FakeKotlinSourcesWithoutGeneratedDirectory

    private class ThrowingFakeKotlinSources {
        @Suppress("UNUSED_PARAMETER")
        fun addGeneratedSourceDirectory(
            taskProvider: TaskProvider<out Task>,
            accessor: (GenerateKayanConfigTask) -> DirectoryProperty,
        ) {
            accessor(taskProvider.get() as GenerateKayanConfigTask)
            error("boom")
        }
    }
}
