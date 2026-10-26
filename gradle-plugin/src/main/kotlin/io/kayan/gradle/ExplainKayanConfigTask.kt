package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.kayan.ConfigFormat
import io.kayan.KayanValidationMode
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Explains configuration to the console on every invocation.")
internal abstract class ExplainKayanConfigTask : DefaultTask() {
    @get:Optional
    @get:Input
    public abstract val flavor: Property<String>

    @get:Optional
    @get:Input
    public abstract val target: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val baseConfigFile: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val customConfigFile: RegularFileProperty

    @get:Input
    public abstract val configFormat: Property<ConfigFormat>

    @get:Input
    public abstract val validationMode: Property<KayanValidationMode>

    @get:Input
    public abstract val schemaEntries: ListProperty<String>

    @Option(option = "flavor", description = "Flavor to explain (defaults to kayan.flavor).")
    public fun selectFlavor(value: String) {
        flavor.set(value)
    }

    @Option(option = "target", description = "Apply overlays for this target (omitted by default).")
    public fun selectTarget(value: String) {
        target.set(value)
    }

    @TaskAction
    public fun explain() {
        val explanation = explanationEither().getOrElse { throw it.toGradleException() }
        logger.lifecycle(explanation)
    }

    private fun explanationEither(): Either<KayanGradleError, String> = either {
        val flavorName = requireConfiguredEither(flavor.orNull, "flavor").bind()
        val targetName = target.orNull?.let { requireConfiguredEither(it, "target").bind() }
        val schema = requireSchemaEither(schemaEntries.get()).bind()
        val trace = resolveConfigWithTraceEither(
            schema = schema,
            baseFile = requireExistingFileEither(baseConfigFile.asFile.get(), "base").bind(),
            customFile = customConfigFile.asFile.orNull?.let { requireExistingFileEither(it, "custom").bind() },
            configFormat = configFormat.get(),
            validationMode = validationMode.get(),
            targetName = targetName,
        ).bind()
        val selected = trace.flavors[flavorName] ?: raise(GenerationError.MissingResolvedFlavor(flavorName))
        renderConfigExplanation(selected)
    }
}

internal fun Project.registerExplainKayanConfigTask(extension: KayanExtension) {
    tasks.register("explainKayanConfig", ExplainKayanConfigTask::class.java) { task ->
        task.group = "help"
        task.description = "Explains resolved Kayan values and their winning sources for a flavor and optional target."
        task.flavor.convention(extension.flavor)
        task.baseConfigFile.convention(extension.baseConfigFile)
        task.customConfigFile.convention(extension.customConfigFile)
        task.configFormat.convention(extension.configFormat)
        task.validationMode.convention(extension.validationMode)
        task.schemaEntries.convention(provider { extension.serializedSchemaEntries() })
    }
}
