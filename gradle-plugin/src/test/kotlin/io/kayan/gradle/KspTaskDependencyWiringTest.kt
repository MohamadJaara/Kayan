package io.kayan.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KspTaskDependencyWiringTest {
    @Test
    fun wiresGenerateTaskIntoMatchingKspTasksAndSkipsUnrelatedOnes() {
        val project = ProjectBuilder.builder().build()
        val matchingDirectory = createTempDirectory(prefix = "ksp-wiring-match").toFile()
        val unrelatedDirectory = createTempDirectory(prefix = "ksp-wiring-other").toFile()
        val sourceDirectorySet = project.objects.sourceDirectorySet("jvmMain", "jvmMain").apply {
            srcDir(matchingDirectory)
        }
        val sourceSet = kotlinSourceSet(sourceDirectorySet)
        val generateTask = project.tasks.register("generateKayanJvmMainConfig", GenerateKayanConfigTask::class.java)
        val matchingTask = project.tasks.register("kspKotlinJvm", FakeKspTask::class.java).get()
        val unrelatedTask = project.tasks.register("kspKotlinIosArm64", FakeKspTask::class.java).get()

        matchingTask.kspConfig.sourceRoots.from(project.objects.fileTree().from(matchingDirectory))
        unrelatedTask.kspConfig.sourceRoots.from(project.objects.fileTree().from(unrelatedDirectory))

        project.wireKspTaskDependencies(sourceSet, generateTask)

        assertTrue("generateKayanJvmMainConfig" in taskDependencyNames(matchingTask))
        assertFalse("generateKayanJvmMainConfig" in taskDependencyNames(unrelatedTask))
    }

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

    @Test
    fun rejectsTasksNamedLikeKspWhenTheyDoNotExposeKspConfig() {
        val project = ProjectBuilder.builder().build()
        val matchingDirectory = createTempDirectory(prefix = "fake-ksp-source-roots").toFile()
        val task = project.tasks.register("kspPretender", NonKspTask::class.java).get()
        val diagnostics = captureKspDiagnostics()

        withCapturedKspDiagnostics(diagnostics) {
            assertFalse(task.isKspTask())
            assertFalse(task.consumesDeclaredSourceDirectories(setOf(matchingDirectory)))
        }

        assertEquals(2, diagnostics.size)
        assertTrue(diagnostics.all { it.contains("Task 'kspPretender'") })
        assertTrue(diagnostics.all { "getKspConfig" in it })
    }

    @Test
    fun returnsFalseForKspTasksWhenNoSourceDirectoriesAreProvided() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("kspKotlinJvm", FakeKspTask::class.java).get()

        assertFalse(task.consumesDeclaredSourceDirectories(emptySet()))
    }

    @Test
    fun returnsFalseWhenKspConfigGetterReturnsNull() {
        val project = ProjectBuilder.builder().build()
        val matchingDirectory = createTempDirectory(prefix = "null-ksp-config").toFile()
        val task = project.tasks.register("kspBrokenJvm", NullConfigKspTask::class.java).get()
        val diagnostics = captureKspDiagnostics()

        withCapturedKspDiagnostics(diagnostics) {
            assertTrue(task.isKspTask())
            assertFalse(task.consumesDeclaredSourceDirectories(setOf(matchingDirectory)))
        }

        assertEquals(1, diagnostics.size)
        assertTrue("Task 'kspBrokenJvm'" in diagnostics.single())
        assertTrue("getKspConfig" in diagnostics.single())
    }

    @Test
    fun supportsDirectoryFileCollectionIterableAndArraySourceRootShapes() {
        val project = ProjectBuilder.builder().build()
        val directoryRoot = File(project.projectDir, "directory-root").apply { mkdirs() }
        val fileRoot = File(project.projectDir, "file-root").apply { mkdirs() }
        val iterableRoot = File(project.projectDir, "iterable-root").apply { mkdirs() }
        val arrayRoot = File(project.projectDir, "array-root").apply { mkdirs() }
        val collectionRoot = File(project.projectDir, "collection-root").apply { mkdirs() }
        val ignoredFile = File(collectionRoot, "ignored.txt").apply {
            writeText("ignored")
        }
        val task = project.tasks.register("kspShapesJvm", FlexibleKspTask::class.java).get()

        task.configHolder = FlexibleKspConfig(
            sourceRoots = listOf(project.layout.projectDirectory.dir("directory-root"), iterableRoot),
            commonSourceRoots = arrayOf(fileRoot, arrayRoot),
            javaSourceRoots = project.files(collectionRoot, ignoredFile),
        )

        assertTrue(
            task.consumesDeclaredSourceDirectories(
                setOf(directoryRoot, iterableRoot, fileRoot, arrayRoot, collectionRoot),
            ),
        )
    }

    @Test
    fun ignoresUnsupportedAndFailingSourceRootShapes() {
        val project = ProjectBuilder.builder().build()
        val matchingDirectory = createTempDirectory(prefix = "unsupported-root").toFile()
        val failingProvider: Provider<Any> = project.provider {
            error("boom")
        }
        val task = project.tasks.register("kspUnsupportedJvm", FlexibleKspTask::class.java).get()

        task.configHolder = FlexibleKspConfig(
            sourceRoots = failingProvider,
            commonSourceRoots = 123,
            javaSourceRoots = null,
        )

        assertFalse(task.consumesDeclaredSourceDirectories(setOf(matchingDirectory)))
    }

    @Test
    fun reportsMissingSourceRootGetterOnKspConfig() {
        val project = ProjectBuilder.builder().build()
        val matchingDirectory = createTempDirectory(prefix = "missing-getter-root").toFile()
        val task = project.tasks.register("kspMissingGetterJvm", FlexibleKspTask::class.java).get()
        val diagnostics = captureKspDiagnostics()

        task.configHolder = MissingCommonAndJavaRootsKspConfig(sourceRoots = matchingDirectory)

        withCapturedKspDiagnostics(diagnostics) {
            assertTrue(task.consumesDeclaredSourceDirectories(setOf(matchingDirectory)))
        }

        assertEquals(2, diagnostics.size)
        assertTrue(diagnostics.any { "getCommonSourceRoots" in it })
        assertTrue(diagnostics.any { "getJavaSourceRoots" in it })
        assertTrue(diagnostics.all { "Task 'kspMissingGetterJvm'" in it })
    }
}

private open class FakeKspTask : DefaultTask() {
    @get:Nested
    val kspConfig: FakeKspConfig = FakeKspConfig(project.objects)
}

private open class FlexibleKspTask : DefaultTask() {
    lateinit var configHolder: Any

    fun getKspConfig(): Any = configHolder
}

private open class NullConfigKspTask : DefaultTask() {

    @Suppress("FunctionOnlyReturningConstant")
    fun getKspConfig(): Any? = null
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

private class FlexibleKspConfig(
    private val sourceRoots: Any?,
    private val commonSourceRoots: Any?,
    private val javaSourceRoots: Any?,
) {
    fun getSourceRoots(): Any? = sourceRoots

    fun getCommonSourceRoots(): Any? = commonSourceRoots

    fun getJavaSourceRoots(): Any? = javaSourceRoots
}

private class MissingCommonAndJavaRootsKspConfig(
    private val sourceRoots: Any?,
) {
    fun getSourceRoots(): Any? = sourceRoots
}

private fun kotlinSourceSet(sourceDirectorySet: org.gradle.api.file.SourceDirectorySet): KotlinSourceSet =
    Proxy.newProxyInstance(
        KotlinSourceSet::class.java.classLoader,
        arrayOf(KotlinSourceSet::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getKotlin" -> sourceDirectorySet
            "toString" -> "FakeKotlinSourceSet"
            else -> error("Unexpected KotlinSourceSet method: ${method.name}")
        }
    } as KotlinSourceSet

private fun taskDependencyNames(task: org.gradle.api.Task): Set<String> =
    task.taskDependencies.getDependencies(null).map { it.name }.toSet()

private fun captureKspDiagnostics(): MutableList<String> = CopyOnWriteArrayList()

private fun withCapturedKspDiagnostics(
    diagnostics: MutableList<String>,
    block: () -> Unit,
) {
    val previousReporter = kspTaskDiagnosticReporter
    kspTaskDiagnosticReporter = { _, message, cause ->
        diagnostics += if (cause == null) {
            message
        } else {
            "$message (${cause::class.java.simpleName})"
        }
    }
    try {
        block()
    } finally {
        kspTaskDiagnosticReporter = previousReporter
    }
}
