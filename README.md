<p align="center">
  <img src="site/public/kayan-icon.png" alt="Kayan icon" width="144">
</p>

<h1 align="center">Kayan</h1>

<p align="center">Typed layered JSON config for Kotlin Multiplatform, JVM, and Android.</p>

<p align="center">
  <a href="https://github.com/MohamadJaara/Kayan/actions/workflows/gradle.yml"><img src="https://github.com/MohamadJaara/Kayan/actions/workflows/gradle.yml/badge.svg" alt="CI"></a>
  <a href="https://codecov.io/github/MohamadJaara/Kayan"><img src="https://codecov.io/github/MohamadJaara/Kayan/graph/badge.svg?branch=main" alt="Codecov coverage"></a>
  <a href="https://central.sonatype.com/artifact/io.github.mohamadjaara/kayan-gradle-plugin"><img src="https://img.shields.io/maven-central/v/io.github.mohamadjaara/kayan-gradle-plugin" alt="Maven Central"></a>
  <a href="https://github.com/MohamadJaara/Kayan/releases"><img src="https://img.shields.io/github/v/release/MohamadJaara/Kayan" alt="Release"></a>
  <a href="https://github.com/MohamadJaara/Kayan/blob/main/LICENSE"><img src="https://img.shields.io/github/license/MohamadJaara/Kayan" alt="License"></a>
  <a href="https://mohamadjaara.github.io/Kayan/"><img src="https://img.shields.io/badge/docs-GitHub%20Pages-blue" alt="Docs"></a>
</p>

Kayan is a Kotlin Gradle plugin that generates a typed Kotlin object from layered JSON config files.
It works with Kotlin Multiplatform, Kotlin/JVM, and Kotlin Android projects, so shared code can read
config directly without platform-specific `BuildConfig` wiring.

The name comes from the Arabic word `كيان` (`Kayan`), which means "entity", "structure", or "being".

## Why Kayan

- Keep config in JSON while exposing a typed Kotlin API.
- Let the consuming app own the schema and generated property names.
- Resolve layered defaults and overrides deterministically at build time.
- Work in shared Kotlin across KMP, JVM, and Android modules.
- Reuse resolved values inside Gradle itself with `buildValue("key")`.

## Quick start

Add Maven Central to Gradle plugin resolution when consuming the published plugin:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Add a config file:

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

Apply the plugin and declare your schema:

```kotlin
plugins {
    kotlin("multiplatform") version "<kotlin-version>"
    id("io.github.mohamadjaara.kayan") version "<kayan-version>"
}

kayan {
    packageName.set("sample.generated")
    flavor.set("prod")

    schema {
        string("api_base_url", "API_BASE_URL", required = true)
        boolean("feature_search_enabled", "FEATURE_SEARCH_ENABLED")
        string("brand_name", "BRAND_NAME")
    }
}
```

Generate the config source:

```bash
./gradlew generateKayanConfig
```

Use the generated object from shared code:

```kotlin
import sample.generated.SampleConfig

val baseUrl = SampleConfig.API_BASE_URL
val searchEnabled = SampleConfig.FEATURE_SEARCH_ENABLED
val brandName = SampleConfig.BRAND_NAME
```

Generated source lands in `build/generated/kayan/kotlin` and is wired into the appropriate source
set automatically.

> `buildValue()` is experimental.
> Opt in with `@file:OptIn(io.kayan.gradle.ExperimentalKayanGradleApi::class)` when using
> the Gradle build-time API from `build.gradle.kts`.

## Build-time config access

When Gradle logic needs the same resolved config, use `buildValue()` directly in
`build.gradle.kts`:

```kotlin
@file:OptIn(io.kayan.gradle.ExperimentalKayanGradleApi::class)

val isSearchEnabled =
    kayan.buildValue("feature_search_enabled")
        .asBoolean()

dependencies {
    if (isSearchEnabled) {
        implementation("com.example:search-sdk:1.0.0")
    }
}
```

This is useful for conditional dependencies, task inputs, and other configuration-time
decisions. Provider variants such as `asStringProvider()` are also available for lazy task
wiring.

## Learn more

- [Overview](https://mohamadjaara.github.io/Kayan/docs/overview/)
- [Quick Start](https://mohamadjaara.github.io/Kayan/docs/quick-start/)
- [Gradle Usage](https://mohamadjaara.github.io/Kayan/docs/gradle-usage/)
- [Build-Time Config Access](https://mohamadjaara.github.io/Kayan/docs/build-time-config/)
- [Resolution Order](https://mohamadjaara.github.io/Kayan/docs/resolution-order/)
- [JSON Shape](https://mohamadjaara.github.io/Kayan/docs/json-shape/)
- [Schema Types](https://mohamadjaara.github.io/Kayan/docs/schema-types/)
- [Validation](https://mohamadjaara.github.io/Kayan/docs/validation/)
- [Schema Export](https://mohamadjaara.github.io/Kayan/docs/schema-export/)
- [BuildConfig Migration](https://mohamadjaara.github.io/Kayan/docs/buildconfig-migration/)
- [White-label Configs](https://mohamadjaara.github.io/Kayan/docs/white-label/)
- [Commands](https://mohamadjaara.github.io/Kayan/docs/commands/)

## Development

Build everything:

```bash
./gradlew build
```

Run plugin tests:

```bash
./gradlew :gradle-plugin:test
```

## Sample app

The sample app in `sample/` demonstrates local plugin consumption, generated config usage, and
Compose Multiplatform targets for desktop, web, and Apple platforms.

Run the desktop sample:

```bash
./gradlew -p sample run
```

Run the web sample:

```bash
./gradlew -p sample wasmJsBrowserDevelopmentRun
```

More sample details are in `sample/README.md`.
