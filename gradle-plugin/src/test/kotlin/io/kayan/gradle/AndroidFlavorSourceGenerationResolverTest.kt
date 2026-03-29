package io.kayan.gradle

import arrow.core.getOrElse
import io.kayan.ConfigFormat
import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalKayanGenerationApi::class)
class AndroidFlavorSourceGenerationResolverTest {
    @Test
    fun resolvesFlavorSpecificGenerationTasksForAndroidVariants() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("kayan", KayanExtension::class.java).apply {
            baseConfigFile.convention(project.layout.projectDirectory.file("default.json"))
            configFormat.convention(ConfigFormat.JSON)
            className.convention("AppConfig")
            packageName.set("sample.config")
            flavor.set("prod")
            androidFlavorSourceSets {
                flavors.set(listOf("prod", "dev", "fdroid"))
            }
        }
        project.extensions.add(
            KayanConfigPlugin.ANDROID_EXTENSION_NAME,
            FakeAndroidExtension(
                productFlavors = listOf(
                    FakeProductFlavor(name = "prod", dimension = "environment"),
                    FakeProductFlavor(name = "dev", dimension = "environment"),
                    FakeProductFlavor(name = "fdroid", dimension = "environment"),
                    FakeProductFlavor(name = "play", dimension = "store"),
                ),
            ),
        )
        val exportSchemaTask = project.tasks.register("exportKayanSchema")
        val defaultGenerateTask = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java)
        val generationTaskRegistrar = GenerationTaskRegistrar(
            project = project,
            extension = extension,
            exportSchemaTask = exportSchemaTask,
        )
        val resolver = AndroidFlavorSourceGenerationResolver(
            project = project,
            extension = extension,
            defaultGenerateTask = defaultGenerateTask,
            generationTaskRegistrar = generationTaskRegistrar,
        )

        val prodTask = requireRight(
            resolver.generationTaskForVariantEither(
                FakeVariant(
                    name = "prodPlayDebug",
                    productFlavors = listOf(
                        FakeVariantFlavor(first = "environment", second = "prod"),
                        FakeVariantFlavor(first = "store", second = "play"),
                    ),
                ),
            ),
        )
        val devTask = requireRight(
            resolver.generationTaskForVariantEither(
                FakeVariant(
                    name = "devDebug",
                    productFlavors = listOf(FakeVariantFlavor(first = "environment", second = "dev")),
                ),
            ),
        )
        val unmatchedTask = requireRight(
            resolver.generationTaskForVariantEither(
                FakeVariant(
                    name = "playOnlyDebug",
                    productFlavors = listOf(FakeVariantFlavor(first = "store", second = "play")),
                ),
            ),
        )

        assertEquals("generateKayanProdConfig", prodTask?.name)
        assertEquals("generateKayanDevConfig", devTask?.name)
        assertNull(unmatchedTask)
        requireRight(resolver.finalizeConfigurationEither())
        assertTrue(
            project.tasks.names.containsAll(
                listOf(
                    "generateKayanAndroidFlavorConfigs",
                    "generateKayanDevConfig",
                    "generateKayanFdroidConfig",
                    "generateKayanProdConfig",
                ),
            ),
        )
    }

    @Test
    fun failsWhenResolverFinalizesMissingAndroidFlavorConfiguration() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("kayan", KayanExtension::class.java).apply {
            baseConfigFile.convention(project.layout.projectDirectory.file("default.json"))
            configFormat.convention(ConfigFormat.JSON)
            className.convention("AppConfig")
            packageName.set("sample.config")
            flavor.set("prod")
            androidFlavorSourceSets {
                flavors.set(listOf("prod", "dev"))
            }
        }
        project.extensions.add(
            KayanConfigPlugin.ANDROID_EXTENSION_NAME,
            FakeAndroidExtension(
                productFlavors = listOf(FakeProductFlavor(name = "prod", dimension = "environment")),
            ),
        )
        val exportSchemaTask = project.tasks.register("exportKayanSchema")
        val resolver = AndroidFlavorSourceGenerationResolver(
            project = project,
            extension = extension,
            defaultGenerateTask = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java),
            generationTaskRegistrar = GenerationTaskRegistrar(
                project = project,
                extension = extension,
                exportSchemaTask = exportSchemaTask,
            ),
        )

        val error = assertFailsWith<GradleException> {
            requireRight(resolver.finalizeConfigurationEither())
        }

        assertMessageContains(
            error,
            "could not find an Android product flavor named 'dev'",
            "Available product flavors: 'prod'.",
        )
    }

    private fun <T> requireRight(result: arrow.core.Either<KayanGradleError, T>): T =
        result.getOrElse { throw it.toGradleException() }

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

    private class FakeVariant(
        private val name: String,
        private val productFlavors: List<FakeVariantFlavor>,
    ) {
        fun getName(): String = name

        fun getProductFlavors(): List<FakeVariantFlavor> = productFlavors
    }

    private class FakeVariantFlavor(
        private val first: String,
        private val second: String,
    ) {
        fun getFirst(): String = first

        fun getSecond(): String = second
    }
}
