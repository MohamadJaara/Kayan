package io.kayan.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.testfixtures.ProjectBuilder
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KspTaskDependencyWiringTest {
    @Test
    fun detectsKspTasksWhoseSourceRootsAreDeclaredThroughProvidersOfFileTrees() {
        val project = ProjectBuilder.builder().build()
        val matchingDirectory = createTempDirectory(prefix = "ksp-source-roots").toFile()
        val unrelatedDirectory = createTempDirectory(prefix = "ksp-source-roots").toFile()
        val task = project.tasks.register("kspKotlinJvm", FakeKspTask::class.java).get()

        task.kspConfig.sourceRoots.from(
            project.provider {
                listOf(project.objects.fileTree().from(matchingDirectory))
            },
        )

        assertTrue(task.isKspTask())
        assertTrue(task.consumesDeclaredSourceDirectories(setOf(matchingDirectory)))
        assertFalse(task.consumesDeclaredSourceDirectories(setOf(unrelatedDirectory)))
    }

    @Test
    fun detectsKspTasksWhoseCommonSourceRootsComeFromSourceDirectorySets() {
        val project = ProjectBuilder.builder().build()
        val matchingDirectory = createTempDirectory(prefix = "ksp-common-source-roots").toFile()
        val sourceDirectorySet = project.objects.sourceDirectorySet("commonMain", "commonMain").apply {
            srcDir(matchingDirectory)
        }
        val task = project.tasks.register("kspCommonMainKotlinMetadata", FakeKspTask::class.java).get()

        task.kspConfig.commonSourceRoots.from(sourceDirectorySet)

        assertTrue(task.consumesDeclaredSourceDirectories(setOf(matchingDirectory)))
    }

    @Test
    fun ignoresTasksThatAreNotKspTasks() {
        val project = ProjectBuilder.builder().build()
        val matchingDirectory = createTempDirectory(prefix = "non-ksp-source-roots").toFile()
        val task = project.tasks.register("compileKotlinJvm", NonKspTask::class.java).get()

        assertFalse(task.isKspTask())
        assertFalse(task.consumesDeclaredSourceDirectories(setOf(matchingDirectory)))
    }
}

private open class FakeKspTask : DefaultTask() {
    @get:Nested
    val kspConfig: FakeKspConfig = FakeKspConfig(project.objects)
}

private open class FakeKspConfig(
    objects: ObjectFactory,
) {
    @get:InputFiles
    val sourceRoots: ConfigurableFileCollection = objects.fileCollection()

    @get:InputFiles
    val commonSourceRoots: ConfigurableFileCollection = objects.fileCollection()

    @get:InputFiles
    val javaSourceRoots: ConfigurableFileCollection = objects.fileCollection()
}

private open class NonKspTask : DefaultTask()
