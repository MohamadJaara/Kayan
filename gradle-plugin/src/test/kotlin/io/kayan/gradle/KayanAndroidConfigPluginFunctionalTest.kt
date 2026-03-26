package io.kayan.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class KayanAndroidConfigPluginFunctionalTest {
    @Test
    fun activatesAndroidFlavorGenerationFromAndroidPluginIdWithoutLegacyKotlinAndroidPlugin() {
        val projectDir = createTempDirectory(prefix = "kayan-android-functional-test").toFile()

        writeFakeAndroidBuildSrc(projectDir)
        writeSettingsGradle(projectDir)
        writeDefaultJson(projectDir)
        File(projectDir, "build.gradle.kts").writeText(
            """
                @file:OptIn(io.kayan.gradle.ExperimentalKayanGenerationApi::class)

                plugins {
                    id("com.android.application")
                    id("io.github.mohamadjaara.kayan")
                }

                repositories {
                    google()
                    mavenCentral()
                }

                kayan {
                    schema {
                        string("bundle_id", "BUNDLE_ID", required = true)
                    }

                    packageName.set("sample.config")
                    flavor.set("prod")

                    androidFlavorSourceSets {
                        flavors.set(listOf("prod"))
                    }
                }
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "assertGeneratedKayanSources", "generateKayanProdConfig").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":assertGeneratedKayanSources")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanProdConfig")?.outcome)
    }

    @Test
    fun registersFlavorAwareAndroidGeneratedSourcesBeforeVariantCallbacksClose() {
        val projectDir = createTempDirectory(prefix = "kayan-android-multiflavor-functional-test").toFile()

        writeFakeAndroidBuildSrc(projectDir)
        writeSettingsGradle(projectDir)
        writeDefaultJson(projectDir)
        File(projectDir, "build.gradle.kts").writeText(
            """
                @file:OptIn(io.kayan.gradle.ExperimentalKayanGenerationApi::class)

                plugins {
                    id("com.android.application")
                    id("io.github.mohamadjaara.kayan")
                }

                repositories {
                    google()
                    mavenCentral()
                }

                kayan {
                    schema {
                        string("bundle_id", "BUNDLE_ID", required = true)
                    }

                    packageName.set("sample.config")
                    flavor.set("prod")

                    androidFlavorSourceSets {
                        flavors.set(listOf("prod", "dev", "fdroid"))
                    }
                }
            """.trimIndent(),
        )

        val result = gradleRunner(
            projectDir,
            "generateKayanAndroidFlavorConfigs",
            "assertMultiFlavorGeneratedKayanSources",
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanAndroidFlavorConfigs")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanProdConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanDevConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanFdroidConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":assertMultiFlavorGeneratedKayanSources")?.outcome)
    }

    private fun writeSettingsGradle(projectDir: File) {
        File(projectDir, "settings.gradle.kts").writeText(
            """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                    }
                }

                dependencyResolutionManagement {
                    repositories {
                        google()
                        mavenCentral()
                    }
                }

                rootProject.name = "sample-android"
            """.trimIndent(),
        )
    }

    private fun writeDefaultJson(projectDir: File) {
        File(projectDir, "default.json").writeText(
            """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    },
                    "dev": {
                      "bundle_id": "com.example.dev"
                    },
                    "fdroid": {
                      "bundle_id": "com.example.fdroid"
                    }
                  }
                }
            """.trimIndent(),
        )
    }

    private fun writeFakeAndroidBuildSrc(projectDir: File) {
        File(projectDir, "buildSrc/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                    plugins {
                        `kotlin-dsl`
                        `java-gradle-plugin`
                    }

                    repositories {
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                    }

                    gradlePlugin {
                        plugins {
                            create("fakeAndroidApplication") {
                                id = "com.android.application"
                                implementationClass = "fake.android.FakeAndroidApplicationPlugin"
                            }
                        }
                    }
                """.trimIndent(),
            )
        }
        File(projectDir, "buildSrc/src/main/kotlin/fake/android/FakeAndroidApplicationPlugin.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                    package fake.android

                    import org.gradle.api.DefaultTask
                    import org.gradle.api.GradleException
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project
                    import org.gradle.api.Task
                    import org.gradle.api.file.DirectoryProperty
                    import org.gradle.api.model.ObjectFactory
                    import org.gradle.api.provider.Property
                    import org.gradle.api.tasks.Internal
                    import org.gradle.api.tasks.TaskAction
                    import org.gradle.api.tasks.TaskProvider
                    import javax.inject.Inject

                    class FakeAndroidApplicationPlugin : Plugin<Project> {
                        override fun apply(project: Project) {
                            project.extensions.create("android", FakeAndroidExtension::class.java).apply {
                                productFlavors += FakeProductFlavor(name = "prod", dimension = "environment")
                                productFlavors += FakeProductFlavor(name = "dev", dimension = "environment")
                                productFlavors += FakeProductFlavor(name = "fdroid", dimension = "environment")
                                productFlavors += FakeProductFlavor(name = "play", dimension = "store")
                            }

                            val androidComponentsExtension = project.extensions.create(
                                "androidComponents",
                                FakeAndroidComponentsExtension::class.java,
                            )
                            androidComponentsExtension.variants += FakeVariant(
                                name = "prodPlayDebug",
                                productFlavors = listOf(
                                    FakeVariantFlavor(first = "environment", second = "prod"),
                                    FakeVariantFlavor(first = "store", second = "play"),
                                ),
                            )
                            androidComponentsExtension.variants += FakeVariant(
                                name = "devPlayDebug",
                                productFlavors = listOf(
                                    FakeVariantFlavor(first = "environment", second = "dev"),
                                    FakeVariantFlavor(first = "store", second = "play"),
                                ),
                            )
                            androidComponentsExtension.variants += FakeVariant(
                                name = "fdroidRelease",
                                productFlavors = listOf(FakeVariantFlavor(first = "environment", second = "fdroid")),
                            )
                            androidComponentsExtension.variants += FakeVariant(
                                name = "playOnlyDebug",
                                productFlavors = listOf(FakeVariantFlavor(first = "store", second = "play")),
                            )

                            project.afterEvaluate {
                                androidComponentsExtension.closeCallbacks()
                                androidComponentsExtension.dispatchVariants()
                            }

                            project.tasks.register(
                                "assertGeneratedKayanSources",
                                AssertGeneratedKayanSourcesTask::class.java,
                            ) {
                                androidComponents.set(androidComponentsExtension)
                            }
                            project.tasks.register(
                                "assertMultiFlavorGeneratedKayanSources",
                                AssertMultiFlavorGeneratedKayanSourcesTask::class.java,
                            ) {
                                androidComponents.set(androidComponentsExtension)
                            }
                        }
                    }

                    abstract class AssertGeneratedKayanSourcesTask : DefaultTask() {
                        @get:Internal
                        abstract val androidComponents: Property<FakeAndroidComponentsExtension>

                        @TaskAction
                        fun assertRegisteredSources() {
                            val registeredTaskNames = androidComponents.get().variants
                                .flatMap { variant -> variant.sources.kotlin.registeredTaskNames }

                            if ("generateKayanProdConfig" !in registeredTaskNames) {
                                throw GradleException(
                                    "Expected generateKayanProdConfig to be registered through the fake Android Variant API.",
                                )
                            }
                        }
                    }

                    abstract class AssertMultiFlavorGeneratedKayanSourcesTask : DefaultTask() {
                        @get:Internal
                        abstract val androidComponents: Property<FakeAndroidComponentsExtension>

                        @TaskAction
                        fun assertRegisteredSources() {
                            val variantsByName = androidComponents.get().variants.associateBy(FakeVariant::name)

                            assertVariantTasks(variantsByName, "prodPlayDebug", listOf("generateKayanProdConfig"))
                            assertVariantTasks(variantsByName, "devPlayDebug", listOf("generateKayanDevConfig"))
                            assertVariantTasks(variantsByName, "fdroidRelease", listOf("generateKayanFdroidConfig"))
                            assertVariantTasks(variantsByName, "playOnlyDebug", emptyList())
                        }

                        private fun assertVariantTasks(
                            variantsByName: Map<String, FakeVariant>,
                            variantName: String,
                            expectedTaskNames: List<String>,
                        ) {
                            val variant = variantsByName[variantName]
                                ?: throw GradleException(
                                    "Missing fake Android variant '" + variantName + "'.",
                                )
                            val actualTaskNames = variant.sources.kotlin.registeredTaskNames

                            if (actualTaskNames != expectedTaskNames) {
                                throw GradleException(
                                    "Expected variant '" + variantName + "' to register " +
                                        expectedTaskNames +
                                        ", but found " +
                                        actualTaskNames +
                                        ".",
                                )
                            }
                        }
                    }

                    abstract class FakeAndroidComponentsExtension @Inject constructor(
                        private val objects: ObjectFactory,
                    ) {
                        private val variantActions: MutableList<org.gradle.api.Action<FakeVariant>> = mutableListOf()
                        private var callbacksClosed: Boolean = false

                        val variants: MutableList<FakeVariant> = mutableListOf()

                        fun selector(): FakeVariantSelector = objects.newInstance(FakeVariantSelector::class.java)

                        fun onVariants(
                            @Suppress("UNUSED_PARAMETER") selector: FakeVariantSelector,
                            action: org.gradle.api.Action<FakeVariant>,
                        ) {
                            if (callbacksClosed) {
                                throw GradleException(
                                    "It is too late to add actions as the callbacks already executed.",
                                )
                            }
                            variantActions += action
                        }

                        fun closeCallbacks() {
                            callbacksClosed = true
                        }

                        fun dispatchVariants() {
                            variantActions.forEach { action ->
                                variants.forEach(action::execute)
                            }
                        }
                    }

                    abstract class FakeVariantSelector @Inject constructor() {
                        fun all(): FakeVariantSelector = this
                    }

                    data class FakeVariant(
                        val name: String,
                        val productFlavors: List<FakeVariantFlavor>,
                        val sources: FakeVariantSources = FakeVariantSources(),
                    )

                    data class FakeVariantFlavor(
                        val first: String,
                        val second: String,
                    )

                    data class FakeVariantSources(
                        val kotlin: FakeKotlinSources = FakeKotlinSources(),
                    )

                    class FakeKotlinSources {
                        val registeredTaskNames: MutableList<String> = mutableListOf()

                        @Suppress("UNUSED_PARAMETER")
                        fun addGeneratedSourceDirectory(
                            taskProvider: TaskProvider<out Task>,
                            accessor: (Any) -> DirectoryProperty,
                        ) {
                            registeredTaskNames += taskProvider.name
                        }
                    }

                    abstract class FakeAndroidExtension @Inject constructor() {
                        val productFlavors: MutableList<FakeProductFlavor> = mutableListOf()
                    }

                    data class FakeProductFlavor(
                        val name: String,
                        val dimension: String?,
                    )
                """.trimIndent(),
            )
        }
    }

    private fun gradleRunner(projectDir: File, vararg tasks: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*tasks, "--stacktrace")
}
