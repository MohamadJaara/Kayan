@file:OptIn(ExperimentalKayanApi::class)

package io.kayan

import arrow.core.Either
import arrow.core.raise.either

internal object CustomConfigValidator {
    fun validateEither(
        defaultConfig: AppConfigFile,
        customConfig: AppConfigFile?,
        defaultConfigSourceName: String,
        customConfigSourceName: String,
    ): Either<ConfigError, Unit> = either {
        customConfig?.flavors?.keys?.forEach { customFlavor ->
            if (customFlavor !in defaultConfig.flavors) {
                raise(
                    ConfigError.UnknownFlavorInCustomConfig(
                        customFlavor = customFlavor,
                        customContext = DiagnosticContext(customConfigSourceName).atFlavor(customFlavor),
                        defaultConfigSourceName = defaultConfigSourceName,
                    ),
                )
            }
        }

        val declaredTargets = defaultConfig.declaredTargets()
        customConfig?.defaults?.targets?.keys?.forEach { customTarget ->
            if (customTarget !in declaredTargets) {
                raise(
                    ConfigError.UnknownTargetInCustomConfig(
                        customTarget = customTarget,
                        customContext = DiagnosticContext(customConfigSourceName).atTarget(customTarget),
                        defaultConfigSourceName = defaultConfigSourceName,
                    ),
                )
            }
        }
        customConfig?.flavors?.forEach { (flavorName, section) ->
            section.targets.keys.forEach { customTarget ->
                if (customTarget !in declaredTargets) {
                    raise(
                        ConfigError.UnknownTargetInCustomConfig(
                            customTarget = customTarget,
                            customContext = DiagnosticContext(customConfigSourceName)
                                .atFlavor(flavorName)
                                .atTarget(customTarget),
                            defaultConfigSourceName = defaultConfigSourceName,
                        ),
                    )
                }
            }
        }

        customConfig?.let {
            validateProtectedCustomOverridesEither(
                customConfig = it,
                customConfigSourceName = customConfigSourceName,
                defaultConfigSourceName = defaultConfigSourceName,
            ).bind()
        }
    }

    private fun validateProtectedCustomOverridesEither(
        customConfig: AppConfigFile,
        customConfigSourceName: String,
        defaultConfigSourceName: String,
    ): Either<ConfigError, Unit> = either {
        validateProtectedSectionOverridesEither(
            section = customConfig.defaults,
            context = DiagnosticContext(customConfigSourceName),
            defaultConfigSourceName = defaultConfigSourceName,
        ).bind()

        customConfig.flavors.forEach { (flavorName, section) ->
            validateProtectedSectionOverridesEither(
                section = section,
                context = DiagnosticContext(customConfigSourceName).atFlavor(flavorName),
                defaultConfigSourceName = defaultConfigSourceName,
            ).bind()
        }
    }

    private fun validateProtectedSectionOverridesEither(
        section: ConfigSection,
        context: DiagnosticContext,
        defaultConfigSourceName: String,
    ): Either<ConfigError, Unit> = either {
        section.values.keys.forEach { definition ->
            if (definition.preventOverride) {
                raise(
                    ConfigError.PreventedCustomOverride(
                        definition = definition,
                        customContext = context.atKey(definition.jsonKey),
                        defaultConfigSourceName = defaultConfigSourceName,
                    ),
                )
            }
        }

        section.targets.forEach { (targetName, targetSection) ->
            validateProtectedSectionOverridesEither(
                section = targetSection,
                context = context.atTarget(targetName),
                defaultConfigSourceName = defaultConfigSourceName,
            ).bind()
        }
    }
}

private fun AppConfigFile.declaredTargets(): Set<String> = buildSet {
    addAll(defaults.targets.keys)
    flavors.values.forEach { section ->
        addAll(section.targets.keys)
    }
}
