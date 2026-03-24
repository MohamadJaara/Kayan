package io.kayan.gradle

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

public abstract class KayanConfigValueSource : ValueSource<String, KayanConfigValueSource.Parameters> {
    public interface Parameters : ValueSourceParameters {
        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        public val baseConfigFile: RegularFileProperty

        @get:Optional
        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        public val customConfigFile: RegularFileProperty

        @get:Input
        public val flavor: Property<String>

        @get:Input
        public val schemaEntries: ListProperty<String>
    }

    override fun obtain(): String =
        obtainEither().getOrElse { throw it.toGradleException() }

    private fun obtainEither(): Either<KayanGradleError, String> = either {
        val schema = requireSchemaEither(parameters.schemaEntries.get()).bind()
        val flavor = requireConfiguredEither(parameters.flavor.orNull, "flavor").bind()
        val baseFile = requireExistingFileEither(parameters.baseConfigFile.asFile.get(), "base").bind()
        val customFile = parameters.customConfigFile.orNull?.asFile?.let { file ->
            requireExistingFileEither(file, "custom").bind()
        }
        val resolved = resolveConfigEither(schema, baseFile, customFile).bind()
        val resolvedFlavor = requireResolvedFlavorEither(resolved, flavor).bind()

        serializeResolvedValues(resolvedFlavor)
    }
}
