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
import org.gradle.work.DisableCachingByDefault

@OptIn(ExperimentalKayanGenerationApi::class)
internal fun registerKayanConfigValidation(project: Project, extension: KayanExtension) {
    val validateTask = project.tasks.register("validateKayanConfig", ValidateKayanConfigTask::class.java) { task ->
        task.group = "verification"
        task.description = "Validates all Kayan config flavors and targets without generating artifacts."
        task.baseConfigFile.set(extension.baseConfigFile)
        task.customConfigFile.set(extension.customConfigFile)
        task.configFormat.set(extension.configFormat)
        task.validationMode.set(extension.validationMode)
        task.schemaEntries.set(project.provider { extension.serializedSchemaEntries() })
        task.targetNames.set(
            project.provider { extension.targetSourceSetMappings().map(KayanTargetSourceSetMapping::targetName) },
        )
    }
    project.tasks.named { taskName -> taskName == "check" }.configureEach { task ->
        task.dependsOn(validateTask)
    }
}

@DisableCachingByDefault(because = "Validation has no outputs and must report its result on every invocation.")
internal abstract class ValidateKayanConfigTask : DefaultTask() {
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

    @get:Input
    public abstract val targetNames: ListProperty<String>

    @TaskAction
    public fun validate() {
        val summary = validateEither().getOrElse { throw it.toGradleException() }
        logger.lifecycle(summary)
    }

    private fun validateEither(): Either<KayanGradleError, String> = either {
        val schema = requireSchemaEither(schemaEntries.get()).bind()
        val baseFile = requireExistingFileEither(baseConfigFile.get().asFile, "base").bind()
        val customFile = customConfigFile.orNull?.asFile?.let { file ->
            requireExistingFileEither(file, "custom").bind()
        }
        validateConfigEither(
            schema = schema,
            baseFile = baseFile,
            customFile = customFile,
            configFormat = configFormat.get(),
            validationMode = validationMode.get(),
            configuredTargetNames = targetNames.get(),
        ).bind()
    }
}
