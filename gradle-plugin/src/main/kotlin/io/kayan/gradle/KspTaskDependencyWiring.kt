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

internal fun Task.isKspTask(): Boolean {
    if (!name.startsWith(KSP_TASK_NAME_PREFIX)) {
        return false
    }

    if (methodOrNull(KSP_CONFIG_GETTER_NAME, 0) == null) {
        reportKspIntrospectionIssue(
            methodName = KSP_CONFIG_GETTER_NAME,
            detail = "Expected a KSP task to expose $KSP_CONFIG_GETTER_NAME(), but it was missing.",
        )
        return false
    }

    return true
}

private fun Task.kspDeclaredSourceDirectories(): Set<File> {
    val kspConfig = invokeKspMethodOrReport(target = this, methodName = KSP_CONFIG_GETTER_NAME) ?: return emptySet()

    return KSP_SOURCE_ROOT_GETTERS
        .flatMapTo(linkedSetOf()) { getterName ->
            invokeKspMethodOrReport(target = kspConfig, methodName = getterName)?.declaredDirectories().orEmpty()
        }
}

private fun Task.invokeKspMethodOrReport(
    target: Any,
    methodName: String,
): Any? {
    val method = target.methodOrNull(methodName, 0)
    if (method == null) {
        reportKspIntrospectionIssue(
            methodName = methodName,
            detail = "Unable to inspect KSP source roots because ${target.javaClass.name} is missing $methodName().",
        )
        return null
    }

    return runCatching {
        method.invoke(target)
    }.getOrElse { error ->
        reportKspIntrospectionIssue(
            methodName = methodName,
            detail = "Unable to inspect KSP source roots because $methodName() on ${target.javaClass.name} failed.",
            cause = error,
        )
        null
    } ?: run {
        reportKspIntrospectionIssue(
            methodName = methodName,
            detail = "Unable to inspect KSP source roots because $methodName() on ${target.javaClass.name} returned null.",
        )
        null
    }
}

private fun Task.reportKspIntrospectionIssue(
    methodName: String,
    detail: String,
    cause: Throwable? = null,
) {
    val message =
        "$detail Task '$name' may be using an unsupported KSP version or API shape; " +
            "Kayan will skip automatic KSP dependency wiring for this task. Missing method: $methodName."

    kspTaskDiagnosticReporter(this, message, cause)
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

internal var kspTaskDiagnosticReporter: (Task, String, Throwable?) -> Unit = { task, message, cause ->
    if (cause == null) {
        task.logger.warn(message)
    } else {
        task.logger.warn(message, cause)
    }
}

private const val KSP_CONFIG_GETTER_NAME: String = "getKspConfig"
private val KSP_SOURCE_ROOT_GETTERS: Set<String> = linkedSetOf("getSourceRoots", "getCommonSourceRoots", "getJavaSourceRoots")
private const val KSP_TASK_NAME_PREFIX: String = "ksp"
