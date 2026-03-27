package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

internal abstract class ExportKayanSchemaTask : DefaultTask() {
    @get:Input
    public abstract val schemaEntries: ListProperty<String>

    @get:Optional
    @get:Input
    public abstract val packageName: Property<String>

    @get:Optional
    @get:Input
    public abstract val className: Property<String>

    @get:OutputFile
    public abstract val jsonSchemaOutputFile: RegularFileProperty

    @get:OutputFile
    public abstract val markdownSchemaOutputFile: RegularFileProperty

    @TaskAction
    public fun export() {
        exportEither().getOrElse { throw it.toGradleException() }
    }

    private fun exportEither(): Either<KayanGradleError, Unit> = either {
        val schema = requireSchemaEither(schemaEntries.orNull.orEmpty()).bind()
        val generatedTypeName = generatedTypeName(
            packageName = packageName.orNull,
            className = className.orNull,
        )
        val generatedJsonSchema = Either.catch {
            KayanSchemaExportGenerator.generateJsonSchema(schema)
        }
        val jsonSchema = when (generatedJsonSchema) {
            is Either.Left -> raise(GenerationError.SourceGenerationFailure("JSON schema", generatedJsonSchema.value))
            is Either.Right -> generatedJsonSchema.value
        }
        val generatedMarkdownSchema = Either.catch {
            KayanSchemaExportGenerator.generateMarkdown(
                schema = schema,
                generatedTypeName = generatedTypeName,
            )
        }
        val markdownSchema = when (generatedMarkdownSchema) {
            is Either.Left -> raise(
                GenerationError.SourceGenerationFailure("Markdown schema", generatedMarkdownSchema.value),
            )
            is Either.Right -> generatedMarkdownSchema.value
        }

        writeEither(jsonSchemaOutputFile.asFile.get(), jsonSchema).bind()
        writeEither(markdownSchemaOutputFile.asFile.get(), markdownSchema).bind()
    }

    private fun generatedTypeName(
        packageName: String?,
        className: String?,
    ): String? {
        val normalizedPackageName = packageName?.trim().orEmpty().ifBlank { null }
        val normalizedClassName = className?.trim().orEmpty().ifBlank { null }

        return when {
            normalizedPackageName != null && normalizedClassName != null -> {
                "$normalizedPackageName.$normalizedClassName"
            }

            normalizedClassName != null -> normalizedClassName
            else -> null
        }
    }

    private fun writeEither(
        outputFile: File,
        content: String,
    ): Either<GenerationError, Unit> = either {
        val parent = outputFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            raise(GenerationError.DirectoryCreationFailure(parent.path))
        }

        val writeResult = Either.catch {
            outputFile.writeText(content)
        }
        when (writeResult) {
            is Either.Left -> raise(GenerationError.FileWriteFailure(outputFile.path, writeResult.value))
            is Either.Right -> writeResult.value
        }
    }
}
