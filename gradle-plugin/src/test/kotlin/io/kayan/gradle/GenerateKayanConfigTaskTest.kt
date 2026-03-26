package io.kayan.gradle

import io.kayan.assertMessageContains
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GenerateKayanConfigTaskTest {
    @Test
    fun generateExplainsSupportedKotlinPluginPathsWhenNoCompatiblePluginWasDetected() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateKayanConfig", GenerateKayanConfigTask::class.java) {
            it.kotlinPluginApplied.set(false)
        }.get()

        val error = assertFailsWith<GradleException> {
            task.generate()
        }

        assertMessageContains(
            error,
            "org.jetbrains.kotlin.multiplatform",
            "org.jetbrains.kotlin.jvm",
            "com.android.application",
            "com.android.library",
        )
    }
}
