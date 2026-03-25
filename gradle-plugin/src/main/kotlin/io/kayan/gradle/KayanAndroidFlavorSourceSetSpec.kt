package io.kayan.gradle

import org.gradle.api.provider.ListProperty

@ExperimentalKayanGenerationApi
public abstract class KayanAndroidFlavorSourceSetSpec {
    public abstract val flavors: ListProperty<String>
}
