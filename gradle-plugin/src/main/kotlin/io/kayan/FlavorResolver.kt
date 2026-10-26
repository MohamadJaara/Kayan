@file:OptIn(ExperimentalKayanApi::class)

package io.kayan

import arrow.core.Either
import arrow.core.raise.either

internal object FlavorResolver {
    fun resolveFlavorsWithTraceEither(
        schema: ConfigSchema,
        defaultConfig: AppConfigFile,
        customConfig: AppConfigFile?,
        targetName: String?,
        defaultConfigSourceName: String,
        customConfigSourceName: String,
    ): Either<ConfigError, Map<String, FlavorResolutionTrace>> = either {
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
                        defaultConfigSourceName = defaultConfigSourceName,
                        customConfigSourceName = customConfigSourceName,
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
        defaultConfigSourceName: String,
        customConfigSourceName: String,
    ): Either<ConfigError, FlavorResolutionTrace> = either {
        val customFlavorValues = customConfig?.flavors?.get(flavorName)
        val layers = resolutionLayers(
            flavorName = flavorName,
            customFlavorValues = customFlavorValues,
            customConfig = customConfig,
            defaultFlavorValues = defaultFlavorValues,
            defaultConfig = defaultConfig,
            targetName = targetName,
            defaultConfigSourceName = defaultConfigSourceName,
            customConfigSourceName = customConfigSourceName,
        )
        val sources = mutableMapOf<ConfigDefinition, ConfigValueSource>()
        val merged = buildMap<ConfigDefinition, ConfigValue?> {
            for (definition in schema.entries) {
                val winningLayer = layers.firstOrNull { it.section?.values?.containsKey(definition) == true }
                put(definition, winningLayer?.section?.values?.get(definition))
                if (winningLayer != null) {
                    sources[definition] = ConfigValueSource(
                        context = winningLayer.context.atKey(definition.jsonKey),
                        layer = winningLayer.layer,
                    )
                }
            }
        }

        requireResolvedRequiredKeysEither(
            flavorName = flavorName,
            targetName = targetName,
            schema = schema,
            merged = merged,
        ).bind()

        FlavorResolutionTrace(
            resolved = ResolvedFlavorConfig(
                flavorName = flavorName,
                targetName = targetName,
                values = merged,
            ),
            sources = sources,
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

    private fun resolutionLayers(
        flavorName: String,
        customFlavorValues: ConfigSection?,
        customConfig: AppConfigFile?,
        defaultFlavorValues: ConfigSection,
        defaultConfig: AppConfigFile,
        targetName: String?,
        defaultConfigSourceName: String,
        customConfigSourceName: String,
    ): List<ResolutionLayer> {
        val baseContext = DiagnosticContext(defaultConfigSourceName)
        val customContext = DiagnosticContext(customConfigSourceName)
        val layers = listOf(
            ResolutionLayer(customFlavorValues, customContext.atFlavor(flavorName), ConfigLayer.CUSTOM_FLAVOR),
            ResolutionLayer(customConfig?.defaults, customContext, ConfigLayer.CUSTOM_ROOT),
            ResolutionLayer(defaultFlavorValues, baseContext.atFlavor(flavorName), ConfigLayer.BASE_FLAVOR),
            ResolutionLayer(defaultConfig.defaults, baseContext, ConfigLayer.BASE_ROOT),
        )
        return buildList {
            for (layer in layers) {
                if (targetName != null) {
                    add(
                        layer.copy(
                            section = layer.section?.targets?.get(targetName),
                            context = layer.context.atTarget(targetName),
                        ),
                    )
                }
                add(layer)
            }
        }
    }
}

private data class ResolutionLayer(val section: ConfigSection?, val context: DiagnosticContext, val layer: ConfigLayer)
