package io.kayan.gradle

import org.gradle.api.provider.ListProperty

/**
 * Configures Android flavors that should receive generated Kayan sources.
 *
 * Each listed flavor gets its own generated source directory and task once the
 * Android plugin has finished configuring variants.
 */
@ExperimentalKayanGenerationApi
public abstract class KayanAndroidFlavorSourceSetSpec {
    /** Flavor names that should get generated config source sets. */
    public abstract val flavors: ListProperty<String>
}
