@file:OptIn(ExperimentalKayanApi::class)

package io.kayan

import arrow.core.Either
import arrow.core.raise.either

internal object FlavorResolver {
    fun resolveFlavorsEither(
        schema: ConfigSchema,
        defaultConfig: AppConfigFile,
        customConfig: AppConfigFile?,
        targetName: String?,
    ): Either<ConfigError, Map<String, ResolvedFlavorConfig>> = either {
        buildMap {
            for ((flavorName, defaultFlavorValues) in defaultConfig.flavors) {
                put(
                    flavorName,
                    resolveFlavorEither(
                        flavorName = flavorName,
                        schema = schema,
                        defaultFlavorValues = defaultFlavorValues,
                        defaultConfig = defaultConfig,
                        customConfig = customConfig,
                        targetName = targetName,
                    ).bind(),
                )
            }
        }
    }

    private fun resolveFlavorEither(
        flavorName: String,
        schema: ConfigSchema,
        defaultFlavorValues: ConfigSection,
        defaultConfig: AppConfigFile,
        customConfig: AppConfigFile?,
        targetName: String?,
    ): Either<ConfigError, ResolvedFlavorConfig> = either {
        val customFlavorValues = customConfig?.flavors?.get(flavorName)
        val merged = buildMap<ConfigDefinition, ConfigValue?> {
            for (definition in schema.entries) {
                put(
                    definition,
                    resolvedValue(
                        definition = definition,
                        customFlavorValues = customFlavorValues,
                        customConfig = customConfig,
                        defaultFlavorValues = defaultFlavorValues,
                        defaultConfig = defaultConfig,
                        targetName = targetName,
                    ),
                )
            }
        }

        requireResolvedRequiredKeysEither(
            flavorName = flavorName,
            targetName = targetName,
            schema = schema,
            merged = merged,
        ).bind()

        ResolvedFlavorConfig(
            flavorName = flavorName,
            targetName = targetName,
            values = merged,
        )
    }

    private fun requireResolvedRequiredKeysEither(
        flavorName: String,
        targetName: String?,
        schema: ConfigSchema,
        merged: Map<ConfigDefinition, ConfigValue?>,
    ): Either<ConfigError, Unit> = either {
        schema.entries
            .filter(ConfigDefinition::required)
            .forEach { definition ->
                if (merged[definition] == null || merged[definition] is ConfigValue.NullValue) {
                    raise(
                        ConfigError.MissingRequiredResolvedKey(
                            flavorName = flavorName,
                            targetName = targetName,
                            definition = definition,
                        ),
                    )
                }
            }
    }

    private fun resolvedValue(
        definition: ConfigDefinition,
        customFlavorValues: ConfigSection?,
        customConfig: AppConfigFile?,
        defaultFlavorValues: ConfigSection,
        defaultConfig: AppConfigFile,
        targetName: String?,
    ): ConfigValue? {
        val sections = if (targetName == null) {
            listOf(
                customFlavorValues,
                customConfig?.defaults,
                defaultFlavorValues,
                defaultConfig.defaults,
            )
        } else {
            listOf(
                customFlavorValues?.targets?.get(targetName),
                customFlavorValues,
                customConfig?.defaults?.targets?.get(targetName),
                customConfig?.defaults,
                defaultFlavorValues.targets[targetName],
                defaultFlavorValues,
                defaultConfig.defaults.targets[targetName],
                defaultConfig.defaults,
            )
        }

        sections.forEach { section ->
            if (section?.values?.containsKey(definition) == true) {
                return section.values.getValue(definition)
            }
        }

        return null
    }
}
