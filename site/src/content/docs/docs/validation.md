---
title: Validation
description: How Kayan validates your config at build time.
---

The Gradle plugin defaults to `KayanValidationMode.SUBSET`. In subset mode,
Kayan validates keys declared in the local schema and ignores undeclared keys,
which lets several modules consume one shared config file.

The lower-level `DefaultConfigResolver` API defaults to strict validation unless
you pass a `KayanValidationMode`. The Gradle plugin uses subset mode because
multi-module builds often share one config file.

Use `KayanValidationMode.STRICT` when a module owns the whole config file and
unknown keys should fail the build:

```kotlin
kayan {
    validationMode.set(io.kayan.KayanValidationMode.STRICT)
}
```

| Mode | Unknown keys | Best fit |
| --- | --- | --- |
| `SUBSET` | Ignored unless declared in this module's schema | Shared config files and multi-module builds |
| `STRICT` | Fail validation | Single-owner config files |

Kayan fails the build in these cases:

- **Missing or invalid `flavors`.** Every config file must contain a `flavors`
  key with an object value.
- **Unknown keys in strict mode.** The config contains a key not declared in the schema.
- **Type mismatches.** A value does not match the declared schema type.
- **Unknown custom flavors.** The custom config introduces a flavor missing from the base config.
- **Missing required values.** A required key does not resolve for a flavor.
- **Blocked overrides.** A custom config sets a key marked `preventOverride`.

Validation errors identify the source file and key path. Flavor-specific errors
also name the flavor.

For shared root schemas, see [Multi-module shared config](../multi-module-shared-config/).
