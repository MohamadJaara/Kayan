---
title: Build-time config access
description: Use resolved Kayan values directly in build.gradle.kts for conditional Gradle logic.
---

`buildValue()` exposes resolved config during Gradle configuration. Build logic can
therefore read the same validated values that Kayan later writes to generated Kotlin.

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

Treat every value returned by `buildValue()` as visible build data. Keep API keys,
passwords, tokens, and other secrets in dedicated secure storage. See
[Security](../security/) for the trust model behind `buildValue()` and custom adapters.

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

`buildValue("key")` returns a `KayanBuildValue` with three accessor groups.

### Eager accessors

These resolve immediately and return plain Kotlin values. Use them when the
build script needs the answer right now, for example inside `if` and `when`:

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

Provider accessors run the same type checks and return a Gradle `Provider<T>`.
Use them for task inputs and other lazy Gradle wiring:

```kotlin
val brandName = kayan.buildValue("brand_name").asString()
val brandNameProvider = kayan.buildValue("brand_name").asStringProvider()
```

- `asString()` resolves immediately during configuration
- `asStringProvider()` defers resolution until Gradle needs the value

That difference matters most when assigning into `Property<T>`, `ListProperty<T>`,
or other provider-based Gradle APIs:

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

At Gradle configuration time, enum accessors return the normalized constant name.
They do not instantiate the enum type:

```kotlin
when (kayan.buildValue("release_stage").asEnumName()) {
    "PROD" -> println("production build")
    "BETA" -> println("beta build")
}
```

`asString()` also works for enum values and returns their normalized constant names.

## Target-specific values

When Gradle logic needs a value resolved with target overlays, pass the target
name as the second argument:

```kotlin
val desktopBundleId =
    kayan.buildValue("bundle_id", "jvm")
        .asString()
```

The target name is the key inside the config file's `targets` object. For KMP
source-set mappings, see [Target-specific generation](../target-specific-generation/).

## Error behavior

`buildValue()` reports these errors during Gradle configuration:

- unknown schema key: `"Key '<key>' is not defined in the Kayan schema"` with close-match suggestions
- type mismatch: `"Key '<key>' is <actual kind>, cannot access as <requested type>"`
- null through non-null accessor: `"Key '<key>' is null; use as<Type>OrNull() instead"`

## Constraints

- `flavor` must be configured before `buildValue()` is used
- keys must still be declared in the Kayan `schema {}`
- build-time access returns primitives and collections that Gradle can serialize
- custom adapters are not applied at configuration time

Gradle build logic usually needs a `Boolean`, `String`, or `List<String>`, not a
consumer-owned domain type. See [Custom adapters](../custom-adapters/) for
generation-time custom type conversion.

## Configuration cache

`buildValue()` uses a Gradle `ValueSource`. A change to either config input invalidates
the resolved value. Configuration-cache entries only serialize the requested key, not
the rest of the resolved config.
