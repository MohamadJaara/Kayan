---
title: Target-Specific Generation
description: Generate one shared Kayan API with target-specific values for KMP and Android builds.
---

Use target-specific generation when shared Kotlin code should import one config API,
but each platform needs different resolved values.

Without target-specific generation, Kayan writes one generated `object` into the
normal source set: `commonMain` for Kotlin Multiplatform and `main` for JVM or
Android. When target source sets are configured for a KMP project, Kayan writes
an `expect object` into `commonMain` and matching `actual object` declarations
into the mapped target source sets.

## Config shape

Target overlays live under a `targets` object. You can define them at the
top level for target defaults, inside a flavor for flavor-specific target values,
or both:

```yaml
brand_name: Example App

targets:
  ios:
    brand_name: Example iOS

flavors:
  prod:
    bundle_id: com.example.app
    targets:
      android:
        bundle_id: com.example.android
      ios:
        bundle_id: com.example.ios
```

For the full precedence order, see [Resolution Order](../resolution-order/).

## Conventional KMP targets

Target-specific source generation is experimental, so opt in at the top of
`build.gradle.kts`:

```kotlin
@file:OptIn(io.kayan.gradle.ExperimentalKayanGenerationApi::class)
```

Then configure the target names that should get generated `actual` sources:

```kotlin
kayan {
    packageName.set("sample.generated")
    flavor.set("prod")

    targets("android", "ios", "jvm")
}
```

The conventional target names map to these Kotlin source sets:

| Target name | Source set |
| --- | --- |
| `android` | `androidMain` |
| `ios` | `iosMain` |
| `jvm` | `jvmMain` |
| `js` | `jsMain` |
| `wasmJs` | `wasmJsMain` |

The target name is also the key Kayan reads from the config file's `targets`
object.

## Custom source-set mappings

Use the DSL form when your source-set name and config target name do not match
the conventional mapping:

```kotlin
kayan {
    targets {
        ios()
        jvm("desktop")
        sourceSet("appleMain", "ios-shared")
    }
}
```

In that example, `jvm("desktop")` writes generated source into `jvmMain`, but
resolves values from `targets.desktop`. The `sourceSet("appleMain", "ios-shared")`
entry writes into `appleMain` and resolves values from `targets.ios-shared`.

Kayan validates the configured source sets before generation. If a source-set
name does not exist in the Kotlin project, the build fails with a Gradle error.

## Target-aware build values

`buildValue()` can resolve target overlays too. Pass the target name when Gradle
logic needs the same target-specific value used for generated source:

```kotlin
@file:OptIn(io.kayan.gradle.ExperimentalKayanGradleApi::class)

val desktopBundleId =
    kayan.buildValue("bundle_id", "jvm")
        .asString()
```

The second argument is the config target name, not necessarily the Kotlin source-set
name.

## Android flavor source sets

Android projects get one generated source set by default. If Android product
flavors need separate generated config per flavor, configure the flavor names:

```kotlin
@file:OptIn(io.kayan.gradle.ExperimentalKayanGenerationApi::class)

kayan {
    packageName.set("sample.generated")
    flavor.set("prod")

    androidFlavorSourceSets {
        flavors.set(listOf("prod", "dev", "fdroid"))
    }
}
```

Kayan registers generation tasks such as `generateKayanProdConfig`,
`generateKayanDevConfig`, and `generateKayanFdroidConfig`, then wires each
generated source directory into the matching Android variant source flow.
