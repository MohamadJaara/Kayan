package io.kayan.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

internal fun Project.wireKspTaskDependencies(
    sourceSet: KotlinSourceSet,
    generateTask: TaskProvider<GenerateKayanConfigTask>,
) {
    tasks.configureEach { task ->
        if (task.consumesKotlinSourceSet(sourceSet)) {
            task.dependsOn(generateTask)
        }
    }
}

private fun Task.consumesKotlinSourceSet(sourceSet: KotlinSourceSet): Boolean {
    val sourceDirectories = sourceSet.kotlin.srcDirs
        .map(File::normalizedAbsoluteFile)
        .toSet()

    return consumesDeclaredSourceDirectories(sourceDirectories)
}

internal fun Task.consumesDeclaredSourceDirectories(sourceDirectories: Set<File>): Boolean {
    if (!isKspTask() || sourceDirectories.isEmpty()) {
        return false
    }

    return sourceDirectories.intersects(kspDeclaredSourceDirectories())
}

internal fun Task.isKspTask(): Boolean =
    name.startsWith(KSP_TASK_NAME_PREFIX) && methodOrNull("getKspConfig", 0) != null

private fun Task.kspDeclaredSourceDirectories(): Set<File> {
    val kspConfig = invokeNoArgOrNull("getKspConfig") ?: return emptySet()

    return setOf("getSourceRoots", "getCommonSourceRoots", "getJavaSourceRoots")
        .flatMapTo(linkedSetOf()) { getterName ->
            kspConfig.invokeNoArgOrNull(getterName).declaredDirectories()
        }
}

private fun Any?.declaredDirectories(): Set<File> = when (this) {
    null -> emptySet()
    is Provider<*> -> runCatching { get() }.getOrNull().declaredDirectories()
    is Directory -> setOf(asFile.normalizedAbsoluteFile())
    is File -> setOf(normalizedAbsoluteFile())
    is SourceDirectorySet -> srcDirs.mapTo(linkedSetOf(), File::normalizedAbsoluteFile)
    is ConfigurableFileTree -> setOf(dir.normalizedAbsoluteFile())
    is ConfigurableFileCollection -> from.flatMapTo(linkedSetOf(), Any?::declaredDirectories)
    is FileCollection -> files.filter(File::isDirectory).mapTo(linkedSetOf(), File::normalizedAbsoluteFile)
    is Iterable<*> -> flatMapTo(linkedSetOf(), Any?::declaredDirectories)
    is Array<*> -> flatMapTo(linkedSetOf(), Any?::declaredDirectories)
    else -> emptySet()
}

private fun File.normalizedAbsoluteFile(): File = absoluteFile.normalize()

private fun Set<File>.intersects(other: Set<File>): Boolean = any(other::contains)

private const val KSP_TASK_NAME_PREFIX: String = "ksp"
