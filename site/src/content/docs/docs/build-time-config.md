---
title: Build-Time Config Access
description: Use resolved Kayan values directly in build.gradle.kts for conditional Gradle logic.
---

Kayan can expose resolved config values during Gradle configuration, not just in generated
Kotlin source. This lets build logic use the same validated config that shared code
reads later.

Use `buildValue("json_key")` when Gradle decisions need to depend on config:

```kotlin
@file:OptIn(io.kayan.gradle.ExperimentalKayanGradleApi::class)

kayan {
    flavor.set("prod")

    schema {
        boolean("feature_search_enabled",
                "FEATURE_SEARCH_ENABLED")
        string("brand_name", "BRAND_NAME")
        enumValue("release_stage",
                  "RELEASE_STAGE",
                  enumTypeName = "sample.ReleaseStage")
    }
}

val isSearchEnabled =
    kayan.buildValue("feature_search_enabled")
        .asBoolean()

dependencies {
    if (isSearchEnabled) {
        implementation("com.example:search-sdk:1.0.0")
    }
}
```

By design, this API is meant for non-sensitive build configuration. If a value is appropriate for
Gradle to read during configuration, it is usually fine for `buildValue()`. Secrets such as API
keys, passwords, and tokens should stay in dedicated secret-management or environment-specific
secure storage instead.

## When to use it

:::caution
`buildValue()` is currently experimental.
Opt in with `@file:OptIn(io.kayan.gradle.ExperimentalKayanGradleApi::class)` in
`build.gradle.kts`.
:::

Use `buildValue()` when Gradle itself needs the answer:

- conditional dependencies
- source set or task configuration
- build flags that should be decided during configuration

Use the generated `KayanConfig` object when application or shared Kotlin code needs the
value at compile time or runtime.

## Accessor types

`buildValue("key")` returns a `KayanBuildValue` with three styles of accessors.

### Eager accessors

These resolve immediately and are useful inside `if` and `when`:

```kotlin
kayan.buildValue("brand_name").asString()
kayan.buildValue("feature_search_enabled").asBoolean()
kayan.buildValue("max_workspace_count").asInt()
kayan.buildValue("max_cache_bytes").asLong()
kayan.buildValue("rollout_ratio").asDouble()
kayan.buildValue("support_links").asStringList()
kayan.buildValue("support_labels").asStringMap()
kayan.buildValue("regional_support_links").asStringListMap()
```

### Nullable accessors

If a resolved value may be null, use the `OrNull` variants:

```kotlin
val supportEmail =
    kayan.buildValue("support_email")
        .asStringOrNull()
```

### Provider accessors

For task inputs and other lazy Gradle wiring, use the provider variants:

```kotlin
@file:OptIn(io.kayan.gradle.ExperimentalKayanGradleApi::class)

abstract class PrintBrandTask : DefaultTask() {
    @get:Input
    abstract val brandName: Property<String>

    @TaskAction
    fun printBrand() {
        println(brandName.get())
    }
}

tasks.register<PrintBrandTask>("printBrand") {
    brandName.set(
        kayan.buildValue("brand_name")
            .asStringProvider()
    )
}
```

## Enum values

At Gradle configuration time, enums are exposed by their normalized name rather than by
instantiating the enum type:

```kotlin
when (kayan.buildValue("release_stage").asEnumName()) {
    "PROD" -> println("production build")
    "BETA" -> println("beta build")
}
```

`asString()` also works for enum-backed values.

## Error behavior

`buildValue()` fails early with Gradle-friendly messages:

- unknown schema key: `"Key '<key>' is not defined in the Kayan schema"` with close-match suggestions
- type mismatch: `"Key '<key>' is <actual kind>, cannot access as <requested type>"`
- null through non-null accessor: `"Key '<key>' is null; use as<Type>OrNull() instead"`

## Constraints

- `flavor` must be configured before `buildValue()` is used
- keys must still be declared in the Kayan `schema {}`
- build-time access returns raw Gradle-friendly primitives and collections
- custom adapters are not applied at configuration time

That last point is intentional: Gradle build logic usually needs `Boolean`, `String`, or
`List<String>`, not consumer-owned domain types.

## Configuration cache

`buildValue()` is backed by a Gradle `ValueSource`, so file changes to the configured
inputs invalidate resolution while configuration-cache-friendly builds can still reuse the
requested key between runs without serializing unrelated resolved values.
