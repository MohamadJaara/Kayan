---
title: Multi-Module Shared Config
description: Share one config file and root schema across modules without forcing every module to consume every key.
---

Use `kayanRoot` when several modules read one shared config file, but each module
only needs a subset of the schema. This keeps the file centralized while avoiding
copy-pasted schema blocks in every subproject.

The Gradle plugin defaults to `KayanValidationMode.SUBSET`, which is designed for
this setup: each module validates the keys it declares or includes, while unrelated
keys in the shared file are ignored by that module.

## Root project

Apply the Kayan plugin to the root project and declare shared file conventions
and schema in `kayanRoot`:

```kotlin
plugins {
    id("io.github.mohamadjaara.kayan") version "<kayan-version>"
}

kayanRoot {
    flavor.set(
        providers.gradleProperty("kayanFlavor")
            .orElse("prod")
    )
    baseConfigFile.set(
        layout.projectDirectory.file("config/default.json")
    )
    customConfigFile.set(
        layout.projectDirectory.file("config/brand.json")
    )

    schema {
        string("brand_name", "BRAND_NAME")
        string("api_base_url", "API_BASE_URL", required = true)
        boolean("feature_search_enabled", "FEATURE_SEARCH_ENABLED")
    }
}
```

`kayanRoot` provides conventions for child projects. It does not replace the
normal `kayan` block in a module, because each module still owns its generated
package, class name, and selected schema entries.

## Child modules

In each child module, call `inheritFromRoot()` before including the shared keys
that module consumes:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.mohamadjaara.kayan")
}

kayan {
    packageName.set("sample.feature.generated")
    className.set("FeatureConfig")

    inheritFromRoot()

    schema {
        include("brand_name")
        include("feature_search_enabled")
    }
}
```

Use `includeAll()` when a module should consume the full root schema:

```kotlin
kayan {
    packageName.set("sample.app.generated")

    inheritFromRoot()

    schema {
        includeAll()
    }
}
```

## Rules

- Root schema entries are declared only in `kayanRoot { schema { ... } }`.
- A child module that calls `inheritFromRoot()` cannot add local schema entries.
- A child module must call `include(...)` or `includeAll()` so Kayan knows which
  inherited entries belong to that module.
- Unknown included keys fail early and include close-match suggestions.
- The inherited `flavor`, config files, `configFormat`, and `validationMode` can
  still be overridden locally when a child module needs different conventions.

If a module needs private keys that are not part of the shared schema, keep that
module on a normal local `kayan { schema { ... } }` setup or promote the keys into
`kayanRoot`.

## When to use strict validation

`KayanValidationMode.SUBSET` is usually the right default for shared config files.
Use `KayanValidationMode.STRICT` only when a module owns the whole config document
and every unknown key should fail the build.

```kotlin
kayanRoot {
    validationMode.set(io.kayan.KayanValidationMode.STRICT)
}
```

For more detail, see [Validation](../validation/).
