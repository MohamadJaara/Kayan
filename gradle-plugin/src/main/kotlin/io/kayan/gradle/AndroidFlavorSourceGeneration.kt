package io.kayan.gradle

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import java.util.Locale

private val taskNameSegmentSeparator: Regex = Regex("[^A-Za-z0-9]+")

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

internal fun validateAndroidFlavorDimensionsEither(
    androidExtension: Any?,
    configuredFlavors: List<AndroidFlavorSourceGeneration>,
): Either<PluginConfigurationError, Unit> {
    val dimensionsByFlavor = androidExtension
        ?.androidProductFlavorDimensions()
        .orEmpty()

    val configuredDimensions = configuredFlavors.asSequence()
        .mapNotNull { generation ->
            dimensionsByFlavor[generation.flavorName]?.takeIf(String::isNotBlank)
        }
        .distinct()
        .sorted()
        .toList()

    return if (configuredDimensions.size > 1) {
        PluginConfigurationError.UnsupportedAndroidFlavorDimensions(
            dimensions = configuredDimensions,
            configuredFlavors = configuredFlavors.map(AndroidFlavorSourceGeneration::flavorName),
        ).left()
    } else {
        Unit.right()
    }
}

private fun Any.androidProductFlavorDimensions(): Map<String, String?> {
    val productFlavors = invokeNoArg("getProductFlavors") as? Iterable<*> ?: return emptyMap()

    return buildMap {
        productFlavors.forEach { productFlavor ->
            val name = productFlavor?.readStringProperty("getName")?.takeIf(String::isNotBlank) ?: return@forEach
            val dimension = productFlavor.readStringProperty("getDimension")
                ?: productFlavor.readStringProperty("getFlavorDimension")
            put(name, dimension)
        }
    }
}

private fun Any.readStringProperty(getterName: String): String? =
    invokeNoArg(getterName) as? String

private fun Any.invokeNoArg(methodName: String): Any? =
    runCatching {
        javaClass.getMethod(methodName).invoke(this)
    }.getOrNull()

private fun String.asTaskNameSegment(): String =
    split(taskNameSegmentSeparator)
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
