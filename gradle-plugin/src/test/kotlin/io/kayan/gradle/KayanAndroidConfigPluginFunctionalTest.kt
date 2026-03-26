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
        File(projectDir, "default.json").writeText(
            """
                {
                  "flavors": {
                    "prod": {
                      "bundle_id": "com.example.prod"
                    }
                  }
                }
            """.trimIndent(),
        )

        val result = gradleRunner(projectDir, "assertGeneratedKayanSources", "generateKayanProdConfig").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":assertGeneratedKayanSources")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKayanProdConfig")?.outcome)
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
                            project.extensions.create("android", FakeAndroidExtension::class.java).productFlavors +=
                                FakeProductFlavor(name = "prod", dimension = "environment")

                            val androidComponentsExtension = project.extensions.create(
                                "androidComponents",
                                FakeAndroidComponentsExtension::class.java,
                            )
                            androidComponentsExtension.variants += FakeVariant(
                                name = "prodDebug",
                                productFlavors = listOf(FakeVariantFlavor(first = "environment", second = "prod")),
                            )
                            androidComponentsExtension.variants += FakeVariant(
                                name = "prodRelease",
                                productFlavors = listOf(FakeVariantFlavor(first = "environment", second = "prod")),
                            )

                            val assertGeneratedSourcesTask = project.tasks.register(
                                "assertGeneratedKayanSources",
                                AssertGeneratedKayanSourcesTask::class.java
                            )
                            assertGeneratedSourcesTask.configure {
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

                    abstract class FakeAndroidComponentsExtension @Inject constructor(
                        private val objects: ObjectFactory,
                    ) {
                        val variants: MutableList<FakeVariant> = mutableListOf()

                        fun selector(): FakeVariantSelector = objects.newInstance(FakeVariantSelector::class.java)

                        fun onVariants(selector: FakeVariantSelector, action: org.gradle.api.Action<FakeVariant>) {
                            variants.forEach(action::execute)
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
