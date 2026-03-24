---
title: Gradle Usage
description: How to configure the Kayan plugin in your build.
---

When consuming the published plugin, add Maven Central to plugin resolution first.

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Apply the Kayan plugin alongside any supported Kotlin plugin, then declare package, flavor,
input files, optional format selection, and schema.

```kotlin
plugins {
    // Use any supported Kotlin plugin:
    // kotlin("multiplatform"), kotlin("jvm"), or kotlin("android")
    kotlin("multiplatform") version "<kotlin-version>"
    id("io.github.mohamadjaara.kayan") version "<kayan-version>"
}

kayan {
    packageName.set("sample.generated")
    flavor.set("prod")
    baseConfigFile.set(
        layout.projectDirectory.file("default.json")
    )
    customConfigFile.set(
        layout.projectDirectory.file("custom-overrides.json")
    )
    // Optional when using .yaml or .yml inputs.
    // configFormat.set(io.kayan.ConfigFormat.YAML)
    className.set("SampleConfig")

    schema {
        string("brand_name", "BRAND_NAME")
        string("bundle_id", "BUNDLE_ID",
               required = true)
        boolean("feature_search_enabled",
                "FEATURE_SEARCH_ENABLED")
        stringList("support_links",
                   "SUPPORT_LINKS")
    }
}
```

Generated source is written under `build/generated/kayan/kotlin` and automatically wired
into the appropriate source set (`commonMain` for KMP, `main` for JVM and Android).

Use `.json` files with `ConfigFormat.JSON` and `.yaml` / `.yml` files with `ConfigFormat.YAML`.
`ConfigFormat.AUTO` is available as an experimental opt-in when you want Kayan to infer the format
from file extensions.
