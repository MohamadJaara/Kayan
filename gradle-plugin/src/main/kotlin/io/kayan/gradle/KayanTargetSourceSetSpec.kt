package io.kayan.gradle

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/** Immutable snapshot of one configured target-source-set mapping. */
@ExperimentalKayanGenerationApi
public data class KayanTargetSourceSetMapping(
    /** Kotlin source set that should receive generated target-specific config. */
    public val sourceSetName: String,
    /** Kayan target name used to resolve target overlays for [sourceSetName]. */
    public val targetName: String,
)

/** Mutable entry used inside `kayan { targetSourceSets { ... } }`. */
@ExperimentalKayanGenerationApi
public abstract class KayanTargetSourceSetSpec {
    /** Kotlin source set that should receive generated target-specific config. */
    public abstract val sourceSetName: Property<String>

    /** Kayan target name that should resolve target overlays for this source set. */
    public abstract val targetName: Property<String>
}

/**
 * Configures target-specific KMP source generation.
 *
 * Each configured source set gets its own `actual object` generated with values
 * resolved for the mapped target, while `commonMain` receives the matching
 * `expect object`.
 */
@ExperimentalKayanGenerationApi
public abstract class KayanTargetSourceSetContainer {
    internal val entries: MutableList<KayanTargetSourceSetSpec> = mutableListOf()

    @get:Inject
    internal abstract val objects: ObjectFactory

    /** Adds a target-specific mapping from [sourceSetName] to [targetName]. */
    public fun sourceSet(
        sourceSetName: String,
        targetName: String,
    ) {
        val entry = objects.newInstance(KayanTargetSourceSetSpec::class.java).apply {
            this.sourceSetName.set(sourceSetName)
            this.targetName.set(targetName)
        }
        entries += entry
    }

    /** Configures one target mapping entry with a Gradle [Action]. */
    public fun sourceSet(action: Action<in KayanTargetSourceSetSpec>) {
        val entry = objects.newInstance(KayanTargetSourceSetSpec::class.java)
        action.execute(entry)
        entries += entry
    }

    /**
     * Adds a conventional mapping for [targetName].
     *
     * Supported values are `android`, `ios`, `jvm`, `js`, and `wasmJs`.
     */
    public fun target(targetName: String) {
        val trimmedTargetName = targetName.trim()
        sourceSet(
            sourceSetName = requireNotNull(conventionalSourceSets[trimmedTargetName]) {
                "Unsupported Kayan target '$targetName'. Use one of: " +
                    conventionalSourceSets.keys.joinToString { "'$it'" } +
                    ", or configure an explicit mapping with sourceSet(\"<sourceSet>\", \"$targetName\")."
            },
            targetName = targetName,
        )
    }

    /** Adds conventional mappings for all [targetNames]. */
    public fun targets(vararg targetNames: String) {
        targetNames.forEach(::target)
    }

    /** Maps the conventional Android source set to [targetName]. */
    public fun android(targetName: String = "android") {
        sourceSet(sourceSetName = "androidMain", targetName = targetName)
    }

    /** Maps the conventional iOS source set to [targetName]. */
    public fun ios(targetName: String = "ios") {
        sourceSet(sourceSetName = "iosMain", targetName = targetName)
    }

    /** Maps the conventional JVM source set to [targetName]. */
    public fun jvm(targetName: String = "jvm") {
        sourceSet(sourceSetName = "jvmMain", targetName = targetName)
    }

    /** Maps the conventional JS source set to [targetName]. */
    public fun js(targetName: String = "js") {
        sourceSet(sourceSetName = "jsMain", targetName = targetName)
    }

    /** Maps the conventional Wasm JS source set to [targetName]. */
    public fun wasmJs(targetName: String = "wasmJs") {
        sourceSet(sourceSetName = "wasmJsMain", targetName = targetName)
    }

    private companion object {
        private val conventionalSourceSets: Map<String, String> = linkedMapOf(
            "android" to "androidMain",
            "ios" to "iosMain",
            "jvm" to "jvmMain",
            "js" to "jsMain",
            "wasmJs" to "wasmJsMain",
        )
    }
}
