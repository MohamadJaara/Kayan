package io.kayan

/** Internal tooling view of the same validated result consumed by generation. */
internal data class ConfigResolutionTrace(val flavors: Map<String, FlavorResolutionTrace>) {
    val resolved: ResolvedConfigsByFlavor
        get() = ResolvedConfigsByFlavor(flavors.mapValues { (_, trace) -> trace.resolved })
}

internal data class FlavorResolutionTrace(
    val resolved: ResolvedFlavorConfig,
    // Missing keys have no source; an explicit NullValue still has a winning source.
    val sources: Map<ConfigDefinition, ConfigValueSource>,
)

internal data class ConfigValueSource(val context: DiagnosticContext, val layer: ConfigLayer)

internal enum class ConfigLayer(val description: String) {
    BASE_ROOT("base root defaults"),
    BASE_FLAVOR("base flavor"),
    CUSTOM_ROOT("custom root defaults"),
    CUSTOM_FLAVOR("custom flavor"),
}
