@file:OptIn(io.kayan.ExperimentalKayanApi::class)

package io.kayan.gradle

import arrow.core.Either
import arrow.core.raise.either
import io.kayan.ConfigFormat
import io.kayan.ConfigSchema
import io.kayan.DefaultConfigResolver
import io.kayan.KayanValidationMode
import io.kayan.parserFor
import io.kayan.resolveConfigFormatEither
import java.io.File

internal fun validateConfigEither(
    schema: ConfigSchema,
    baseFile: File,
    customFile: File?,
    configFormat: ConfigFormat,
    validationMode: KayanValidationMode,
    configuredTargetNames: List<String>,
): Either<GenerationError, String> = either {
    val baseText = readFileEither(baseFile).bind()
    val customText = customFile?.let { readFileEither(it).bind() }
    val format = resolveConfigFormatEither(
        baseSourceName = baseFile.absolutePath,
        customSourceName = customFile?.absolutePath,
        configuredFormat = configFormat,
    ).mapLeft(GenerationError::ConfigResolutionFailure).bind()
    val resolver = DefaultConfigResolver(parserFor(format))
    val targets = configuredTargetNames.distinct().ifEmpty {
        val base = resolver.parseEither(baseText, schema, baseFile.absolutePath, validationMode)
            .mapLeft(GenerationError::ConfigResolutionFailure).bind()
        buildSet {
            addAll(base.defaults.targets.keys)
            base.flavors.values.forEach { section -> addAll(section.targets.keys) }
        }.toList()
    }

    // Required values may exist only in target overlays; do not resolve a base-only view in that case.
    var flavorCount = 0
    val resolutionTargets: List<String?> = targets.ifEmpty { listOf(null) }
    resolutionTargets.forEach { targetName ->
        val resolved = resolver.resolveEither(
            defaultConfigJson = baseText,
            schema = schema,
            customConfigJson = customText,
            defaultConfigSourceName = baseFile.absolutePath,
            customConfigSourceName = customFile?.absolutePath ?: "custom config",
            targetName = targetName,
            validationMode = validationMode,
        ).mapLeft(GenerationError::ConfigResolutionFailure).bind()
        flavorCount = resolved.flavors.size
    }

    val scope = if (targets.isEmpty()) {
        "without target overlays"
    } else {
        "for targets ${targets.joinToString { "'$it'" }}"
    }
    "Kayan configuration is valid: $flavorCount flavor(s) $scope (${validationMode.name})."
}
