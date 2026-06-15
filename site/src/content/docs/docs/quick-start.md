---
title: Quick Start
description: Four steps to typed config with Kayan.
---

## 1. Add your config

Save the default JSON config as `default.json`:

```json
{
  "flavors": {
    "prod": {
      "api_base_url": "https://api.example.com",
      "feature_search_enabled": false
    },
    "dev": {
      "api_base_url": "https://dev.example.com",
      "feature_search_enabled": true
    }
  },
  "brand_name": "Example App"
}
```

YAML works with the same shape:

```yaml
flavors:
  prod:
    api_base_url: https://api.example.com
    feature_search_enabled: false
  dev:
    api_base_url: https://dev.example.com
    feature_search_enabled: true
brand_name: Example App
```

If you use YAML, save the file as `default.yml` or `default.yaml` and set
`configFormat` in the Gradle block below.

## 2. Apply the plugin and declare schema

```kotlin
plugins {
    // Use any supported Kotlin plugin:
    // kotlin("multiplatform"), kotlin("jvm"), or kotlin("android")
    kotlin("multiplatform") version "<kotlin-version>"
    id("io.github.mohamadjaara.kayan") version "<kayan-version>"
}

kayan {
    packageName.set("sample.generated")
    className.set("SampleConfig")
    flavor.set("prod")

    // Keep the default for default.json, or use YAML explicitly:
    // baseConfigFile.set(layout.projectDirectory.file("default.yml"))
    // configFormat.set(io.kayan.ConfigFormat.YAML)

    schema {
        string("api_base_url", "API_BASE_URL",
               required = true)
        boolean("feature_search_enabled",
                "FEATURE_SEARCH_ENABLED")
        string("brand_name", "BRAND_NAME")
    }
}
```

:::note
Add `mavenCentral()` to `pluginManagement.repositories` when resolving the published plugin.
:::

## 3. Build to generate

```bash
./gradlew generateKayanConfig
```

Generated source lands in `build/generated/kayan/kotlin` and is wired into the appropriate
source set automatically (`commonMain` for KMP, `main` for JVM/Android).

## 4. Use generated values

```kotlin
import sample.generated.SampleConfig

val baseUrl = SampleConfig.API_BASE_URL
val search  = SampleConfig.FEATURE_SEARCH_ENABLED
val brand   = SampleConfig.BRAND_NAME
```
