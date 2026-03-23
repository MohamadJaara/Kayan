<p align="center">
  <img src="site/kayan-icon.png" alt="Kayan icon" width="144">
</p>

<h1 align="center">Kayan</h1>

<p align="center">Typed layered JSON config for Kotlin Multiplatform.</p>

Kayan is a small, opinionated Kotlin Multiplatform library for apps that keep configuration in layered JSON files and want a typed API in shared code instead of wiring everything through platform-specific build config. Its opinion is simple: keep one shared base config, layer optional overrides on top, and generate a typed object that `commonMain` can read directly. It is especially useful when you have multiple flavors or white-label builds that share most of the app but need different compile time config values.

The name comes from the Arabic word `كيان` (`Kayan`), which means "entity", "structure", or "being".

## What it does

- keeps configuration in JSON while exposing a typed Kotlin API
- lets the consuming app own the schema and generated property names
- works in shared Kotlin instead of Android-only `BuildConfig`
- enforces deterministic merge rules and strict validation at build time

## Modules

- `gradle-plugin/`
  Gradle plugin that generates the typed Kotlin object and contains the internal config resolver/model. Published as `io.github.mohamadjaara:kayan-gradle-plugin`.
- `sample/`
  Standalone Compose Multiplatform app that consumes the local plugin with `includeBuild("..")`.
- `site/`
  Static landing page and documentation site published with GitHub Pages.

## Installation

If you consume the published plugin, make Maven Central available to Gradle plugin resolution:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Then apply the plugin with an explicit version:

```kotlin
plugins {
    kotlin("multiplatform") version "<kotlin-version>"
    id("io.github.mohamadjaara.kayan") version "<kayan-version>"
}
```

## Resolution order

For a selected flavor, values are resolved in this order:

1. custom config flavor value
2. custom config top-level default value
3. base config flavor value
4. base config top-level default value

For example, if the selected flavor is `prod` and the same key is defined in all four places, Kayan chooses the value in this priority order:

| Priority | Value | Why it wins |
| --- | --- | --- |
| 1 | `My Custom App - Prod Flavor` | Custom config for the selected flavor |
| 2 | `My Custom App` | Custom config top-level default |
| 3 | `Base App - Prod Flavor` | Base config for the selected flavor |
| 4 | `Base App` | Base config top-level default |

If a schema entry is marked `required`, it must resolve for every flavor or the build fails.

## JSON shape

Kayan expects a root object with a required `flavors` object. Top-level keys act as defaults for every flavor.

```json
{
  "flavors": {
    "prod": {
      "bundle_id": "com.example.app",
      "feature_search_enabled": false
    },
    "dev": {
      "bundle_id": "com.example.app.dev",
      "feature_search_enabled": true
    }
  },
  "brand_name": "Example App",
  "onboarding_enabled": true,
  "api_base_url": "https://api.example.com",
  "theme_name": "sunrise",
  "support_links": [
    "https://example.com/help"
  ]
}
```

An optional custom override file uses the same shape:

```json
{
  "flavors": {
    "prod": {
      "brand_name": "Example App Custom",
      "feature_search_enabled": true
    }
  },
  "support_links": [
    "https://custom.example.com/help"
  ]
}
```

## Supported schema types

- `string`
- `boolean`
- `int`
- `long`
- `double`
- `stringMap`
- `stringList`
- `stringListMap`
- `enumValue`
- `custom`

`stringMap` maps to `Map<String, String>`.
`stringListMap` maps to `Map<String, List<String>>`.
`enumValue` maps a raw JSON string to a generated Kotlin enum constant using normalized enum names.

Built-in schema entries can also opt into:

- `nullable = true` to allow explicit `null` in JSON and let higher-precedence config clear a lower-precedence value

`custom` lets the Gradle plugin parse one of the built-in raw JSON shapes and emit your final Kotlin expression directly.

## Gradle usage

Apply the Kotlin Multiplatform plugin and the Kayan plugin, then declare the package, target flavor, input files, and schema:

```kotlin
plugins {
    kotlin("multiplatform") version "<kotlin-version>"
    id("io.github.mohamadjaara.kayan") version "<kayan-version>"
}

kayan {
    packageName.set("sample.generated")
    flavor.set("prod")
    baseConfigFile.set(layout.projectDirectory.file("default.json"))
    customConfigFile.set(layout.projectDirectory.file("custom-overrides.json"))
    className.set("SampleConfig")

    schema {
        string("brand_name", "BRAND_NAME")
        string("bundle_id", "BUNDLE_ID", required = true)
        boolean("onboarding_enabled", "ONBOARDING_ENABLED")
        string("api_base_url", "API_BASE_URL")
        string("theme_name", "THEME_NAME")
        boolean("feature_search_enabled", "FEATURE_SEARCH_ENABLED")
        long("max_cache_bytes", "MAX_CACHE_BYTES")
        double("rollout_ratio", "ROLLOUT_RATIO")
        stringMap("support_labels", "SUPPORT_LABELS")
        enumValue("release_stage", "RELEASE_STAGE", "sample.ReleaseStage")
        string("support_email", "SUPPORT_EMAIL", nullable = true)
        stringList("support_links", "SUPPORT_LINKS")
    }
}
```

`required` and `nullable` are not interchangeable:

- `required = true` means the resolved flavor must end with a non-null value.
- `nullable = true` allows explicit `null` in JSON and preserves it through merge precedence.

Custom types are handled with a build-time adapter:

```kotlin
kayan {
    schema {
        custom(
            jsonKey = "launch_date",
            propertyName = "LAUNCH_DATE",
            rawKind = io.kayan.ConfigValueKind.STRING,
            adapter = "sample.buildlogic.LaunchDateAdapter",
            required = true,
        )
    }
}
```

The adapter must be available on the Gradle build classpath, for example from `buildSrc` or another build-logic module. It is not loaded from `commonMain`:

```kotlin
package sample.buildlogic

import io.kayan.ConfigValueKind
import io.kayan.gradle.BuildTimeConfigAdapter

object LaunchDateAdapter : BuildTimeConfigAdapter<String> {
    override val rawKind: ConfigValueKind = ConfigValueKind.STRING
    override val kotlinType: String = "sample.config.LaunchDate"

    override fun parse(rawValue: Any): String = rawValue as String

    override fun renderKotlin(value: String): String = "sample.config.LaunchDate(${quote(value)})"

    private fun quote(value: String): String = "\"${value.replace("\"", "\\\"")}\""
}
```

That lets Kayan generate the final Kotlin expression directly:

```kotlin
public val LAUNCH_DATE: sample.config.LaunchDate = sample.config.LaunchDate("2026-03-21")
```

Notes:

- `packageName` is required.
- `flavor` is required.
- `className` defaults to `KayanConfig`.
- `baseConfigFile` defaults to `default.json`.
- `customConfigFile` is optional.
- `jsonSchemaOutputFile` defaults to `build/generated/kayan/schema/kayan.schema.json`.
- `markdownSchemaOutputFile` defaults to `build/generated/kayan/schema/SCHEMA.md`.

The plugin generates Kotlin source under `build/generated/kayan/commonMain/kotlin` and wires it into `commonMain`.
It also exports JSON Schema and Markdown documentation through `exportKayanSchema`, and `generateKayanConfig` runs that export automatically first.

## Schema export

Kayan can export the consumer-owned Gradle DSL schema as:

- JSON Schema for external validators, editors, or CI checks
- Markdown documentation for human-facing config references

By default the plugin writes both files under `build/generated/kayan/schema/`. If you want them checked in and kept in sync automatically, point the outputs at project files:

```kotlin
kayan {
    packageName.set("sample.generated")
    flavor.set("prod")
    jsonSchemaOutputFile.set(layout.projectDirectory.file("config/kayan.schema.json"))
    markdownSchemaOutputFile.set(layout.projectDirectory.file("docs/config-schema.md"))
}
```

Export explicitly with:

```bash
./gradlew exportKayanSchema
```

The generated JSON Schema validates:

- the required `flavors` object
- unknown keys at the top level and inside each flavor
- raw JSON types for every declared schema entry
- `required` keys by enforcing that they appear either at the top level or in every flavor

The generated Markdown mirrors the same DSL entries, property names, and custom adapter notes so the docs stay aligned with the schema you actually build against.

## Generated API

With the sample schema above, Kayan generates an object like this:

```kotlin
package sample.generated

public object SampleConfig {
    public val BRAND_NAME: String = "Example App Custom"
    public const val BUNDLE_ID: String = "com.example.app"
    public val ONBOARDING_ENABLED: Boolean = true
    public val API_BASE_URL: String = "https://api.example.com"
    public val FEATURE_SEARCH_ENABLED: Boolean = true
    public val SUPPORT_LINKS: List<String> = listOf("https://custom.example.com/help")
}
```

You can then consume it from shared code:

```kotlin
import sample.generated.SampleConfig

val bundleId = SampleConfig.BUNDLE_ID
val searchEnabled = SampleConfig.FEATURE_SEARCH_ENABLED
```

Optional unresolved values are generated as nullable properties.

## Adopting from BuildConfig

Kayan works best as a gradual replacement for Android-only `BuildConfig` constants.

Use this migration path:

1. inventory the constants you actually read from shared code
2. move those values into `default.json`
3. move flavor-specific values into the `flavors` object
4. declare matching schema entries in `kayan { schema { ... } }`
5. keep the generated property names the same as the old `BuildConfig` names to reduce call-site churn
6. replace imports in shared code from `BuildConfig` to your generated Kayan object

If your current code looks like this:

```kotlin
val baseUrl = BuildConfig.API_BASE_URL
val searchEnabled = BuildConfig.FEATURE_SEARCH_ENABLED
```

you can usually migrate with a mechanical import change:

```kotlin
import sample.generated.SampleConfig

val baseUrl = SampleConfig.API_BASE_URL
val searchEnabled = SampleConfig.FEATURE_SEARCH_ENABLED
```

Mapping guidelines:

- values shared by every build belong at the top level of `default.json`
- values that differ by `debug`, `release`, `prod`, `staging`, or similar belong under `flavors`
- values that differ by customer or branded app belong in a separate override file wired through `customConfigFile`
- mark keys as `required = true` when they were previously assumed to always exist in `BuildConfig`

For the first pass, keep the old constant names even if the JSON keys become more descriptive:

```kotlin
kayan {
    schema {
        string("api_base_url", "API_BASE_URL", required = true)
        boolean("feature_search_enabled", "FEATURE_SEARCH_ENABLED")
    }
}
```

That lets you migrate storage and generation first, then rename call sites later if you want a cleaner API.

## Structuring white-label configs

For white-label apps, the opinionated setup is: keep one shared base file in the app repo, and resolve one override file per brand at build time. Put environment differences in `flavors`, and put brand differences in the override file.

That means the app codebase can stay single-repo and reusable. The branded JSON does not need to live next to the app, and the consumer does not need to fork the project just to ship another branded build.

Recommended layout:

```text
config/
  default.json
  brands/
    wafflewizard.json
    bananabeacon.json
```

Example `config/default.json`:

```json
{
  "flavors": {
    "prod": {
      "bundle_id": "com.example.app",
      "api_base_url": "https://api.example.com"
    },
    "staging": {
      "bundle_id": "com.example.app.staging",
      "api_base_url": "https://staging-api.example.com"
    }
  },
  "brand_name": "Example",
  "theme_name": "default",
  "support_links": [
    "https://example.com/help"
  ]
}
```

Example `config/brands/wafflewizard.json`:

```json
{
  "flavors": {
    "prod": {
      "bundle_id": "com.example.wafflewizard"
    },
    "staging": {
      "bundle_id": "com.example.wafflewizard.staging"
    }
  },
  "brand_name": "Waffle Wizard",
  "theme_name": "wafflewizard",
  "support_links": [
    "https://wafflewizard.example.com/help"
  ]
}
```

If the brand files are checked into the same repo, selecting them from Gradle is enough:

```kotlin
val brand = providers.gradleProperty("brand").orElse("wafflewizard")
val kayanFlavor = providers.gradleProperty("kayanFlavor").orElse("prod")

kayan {
    packageName.set("sample.generated")
    className.set("SampleConfig")
    flavor.set(kayanFlavor)
    baseConfigFile.set(layout.projectDirectory.file("config/default.json"))
    customConfigFile.set(brand.map { layout.projectDirectory.file("config/brands/$it.json") })

    schema {
        string("brand_name", "BRAND_NAME", required = true)
        string("bundle_id", "BUNDLE_ID", required = true)
        string("api_base_url", "API_BASE_URL", required = true)
        string("theme_name", "THEME_NAME")
        stringList("support_links", "SUPPORT_LINKS")
    }
}
```

Build examples:

```bash
./gradlew -Pbrand=wafflewizard -PkayanFlavor=prod generateKayanConfig
./gradlew -Pbrand=bananabeacon -PkayanFlavor=staging generateKayanConfig
```

If the brand file lives somewhere else, fetch it before Gradle runs and pass Kayan the resulting file path:

```kotlin
val brandConfigPath =
    providers.gradleProperty("brandConfigPath")
        .orElse(layout.projectDirectory.file("config/brands/wafflewizard.json").asFile.absolutePath)

kayan {
    flavor.set(providers.gradleProperty("kayanFlavor").orElse("prod"))
    baseConfigFile.set(layout.projectDirectory.file("config/default.json"))
    customConfigFile.set(layout.file(brandConfigPath.map { file(it) }))
}
```

That keeps Kayan focused on local file resolution while CI or another repo decides which brand to build:

```bash
./scripts/fetch-brand-config wafflewizard > /tmp/wafflewizard.json
./gradlew generateKayanConfig \
  -PbrandConfigPath=/tmp/wafflewizard.json \
  -PkayanFlavor=prod

curl -o /tmp/bananabeacon.json https://example.com/mobile-branding/bananabeacon.json
./gradlew generateKayanConfig \
  -PbrandConfigPath=/tmp/bananabeacon.json \
  -PkayanFlavor=staging
```

Typical setups:

- another Git repo checked out beside the app repo, with `-PbrandConfigPath=/path/to/branding/wafflewizard.json`
- a CI step that downloads the brand file from object storage or an internal service before Gradle runs
- a wrapper script that materializes the brand file in `/tmp` or `build/` and then calls Gradle

The important part is that Kayan only needs a file path by generation time. How you materialize that file is up to the consuming build, and it does not need to be checked into the app repo.

This structure keeps the merge model simple:

- `default.json` owns the shared schema and sane defaults
- `flavors` handles environment-specific values such as endpoints, bundle IDs, or feature flags
- one override file per brand keeps customer-specific values isolated and easy to diff
- external override files let you ship multiple branded apps without branching or forking the app codebase
- because custom flavors must already exist in the base config, every brand stays aligned to the same environment matrix

## Validation behavior

Kayan fails the build when:

- `flavors` is missing or not an object
- a config contains a key not declared in the schema
- a value does not match the declared schema type
- the custom config introduces a flavor missing from the base config
- a required key does not resolve for a flavor

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

The sample app in `sample/` demonstrates local plugin consumption, generated config usage, and Compose Multiplatform targets for desktop, web, and Apple platforms.

Run the desktop sample:

```bash
./gradlew -p sample run
```

Run the web sample:

```bash
./gradlew -p sample wasmJsBrowserDevelopmentRun
```

Generate config only:

```bash
./gradlew -p sample generateKayanConfig
```

Export JSON Schema and Markdown docs:

```bash
./gradlew -p sample exportKayanSchema
```

More sample details are in `sample/README.md`.
