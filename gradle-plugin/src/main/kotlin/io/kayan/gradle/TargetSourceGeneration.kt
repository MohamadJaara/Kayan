@file:OptIn(ExperimentalKayanGenerationApi::class)

package io.kayan.gradle

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right

internal data class TargetSourceGeneration(
    val sourceSetName: String,
    val targetName: String,
    val taskName: String,
)

internal fun targetSourceGenerationsEither(
    configuredMappings: List<KayanTargetSourceSetMapping>,
): Either<PluginConfigurationError, List<TargetSourceGeneration>> = either {
    val entriesBySourceSet = linkedMapOf<String, String>()

    configuredMappings.forEachIndexed { index, mapping ->
        val sourceSetName = mapping.sourceSetName.trim()
        val targetName = mapping.targetName.trim()

        if (sourceSetName.isEmpty()) {
            raise(PluginConfigurationError.BlankTargetSourceSetName(index))
        }
        if (targetName.isEmpty()) {
            raise(PluginConfigurationError.BlankTargetName(index))
        }

        val previousTarget = entriesBySourceSet[sourceSetName]
        if (previousTarget != null && previousTarget != targetName) {
            raise(
                PluginConfigurationError.DuplicateTargetSourceSet(
                    sourceSetName = sourceSetName,
                    firstTargetName = previousTarget,
                    duplicateTargetName = targetName,
                ),
            )
        }

        entriesBySourceSet[sourceSetName] = targetName
    }

    entriesBySourceSet.map { (sourceSetName, targetName) ->
        TargetSourceGeneration(
            sourceSetName = sourceSetName,
            targetName = targetName,
            taskName = "generateKayan${sourceSetName.asTaskNameSegment()}Config",
        )
    }
}

internal fun validateConfiguredSourceSetsEither(
    availableSourceSets: Set<String>,
    configuredGenerations: List<TargetSourceGeneration>,
): Either<PluginConfigurationError, Unit> {
    val missingSourceSet = configuredGenerations.firstOrNull { generation ->
        generation.sourceSetName !in availableSourceSets
    } ?: return Unit.right()

    return PluginConfigurationError.MissingKotlinSourceSet(
        sourceSetName = missingSourceSet.sourceSetName,
        availableSourceSets = availableSourceSets.sorted(),
    ).left()
}
