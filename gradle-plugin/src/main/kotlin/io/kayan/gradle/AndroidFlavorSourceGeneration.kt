package io.kayan.gradle

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import java.util.Locale

internal data class AndroidFlavorSourceGeneration(
    val flavorName: String,
    val taskName: String,
    val outputDirectorySegment: String,
)

internal fun androidFlavorSourceGenerationsEither(
    configuredFlavors: List<String>,
): Either<PluginConfigurationError, List<AndroidFlavorSourceGeneration>> = either {
    val distinctFlavors = linkedSetOf<String>()

    configuredFlavors.forEachIndexed { index, configuredFlavor ->
        val flavorName = configuredFlavor.trim()
        if (flavorName.isEmpty()) {
            raise(PluginConfigurationError.BlankAndroidFlavorName(index))
        }
        distinctFlavors += flavorName
    }

    distinctFlavors.map { flavorName ->
        AndroidFlavorSourceGeneration(
            flavorName = flavorName,
            taskName = "generateKayan${flavorName.asTaskNameSegment()}Config",
            outputDirectorySegment = flavorName,
        )
    }
}

internal fun validateAndroidFlavorSourceSetsEither(
    configuredFlavors: List<AndroidFlavorSourceGeneration>,
    availableSourceSetNames: Set<String>,
): Either<PluginConfigurationError, Unit> {
    val missingFlavor = configuredFlavors.firstOrNull { generation ->
        generation.flavorName !in availableSourceSetNames
    } ?: return Unit.right()

    return PluginConfigurationError.MissingAndroidFlavorSourceSet(
        flavorName = missingFlavor.flavorName,
        availableSourceSets = availableSourceSetNames.toList().sorted(),
    ).left()
}

private fun String.asTaskNameSegment(): String =
    split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotBlank)
        .joinToString(separator = "") { segment ->
            segment.replaceFirstChar { character ->
                if (character.isLowerCase()) {
                    character.titlecase(Locale.ROOT)
                } else {
                    character.toString()
                }
            }
        }
