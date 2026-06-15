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

Apply the Kayan plugin alongside any supported Kotlin plugin, then declare the
generated package, selected flavor, input files, optional format selection, and schema.

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
    // Required when using .yaml or .yml inputs unless configFormat is AUTO.
    // configFormat.set(io.kayan.ConfigFormat.YAML)
    // configFormat.set(io.kayan.ConfigFormat.AUTO)
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

By design, Kayan targets non-sensitive configuration that you want available as generated Kotlin
code. If a value should not appear in generated sources, build outputs, or normal Gradle
configuration, keep it out of Kayan and use your platform's secret-management approach instead.

## Defaults

Kayan uses these Gradle conventions unless you override them:

| Property | Default |
| --- | --- |
| `baseConfigFile` | `default.json` |
| `configFormat` | `ConfigFormat.JSON` |
| `validationMode` | `KayanValidationMode.SUBSET` |
| `className` | `KayanConfig` |
| `jsonSchemaOutputFile` | `build/generated/kayan/schema/kayan.schema.json` |
| `markdownSchemaOutputFile` | `build/generated/kayan/schema/SCHEMA.md` |

`packageName`, `flavor`, and `schema` must be configured by the consuming build.

## File formats

Use `.json` files with `ConfigFormat.JSON` and `.yaml` / `.yml` files with
`ConfigFormat.YAML`. `ConfigFormat.AUTO` can infer the parser from file
extensions. When both base and custom files are present, `AUTO` requires both
files to use the same concrete format.

## Related configuration

- Use [Target-Specific Generation](../target-specific-generation/) for KMP
  `expect` / `actual` generation and Android flavor-specific source sets.
- Use [Multi-Module Shared Config](../multi-module-shared-config/) when several
  modules share one root config file and schema.
- Use [Validation](../validation/) to choose between subset and strict key
  validation.
