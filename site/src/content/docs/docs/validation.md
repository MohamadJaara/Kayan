---
title: Validation
description: How Kayan validates your config at build time.
---

The Gradle plugin defaults to `KayanValidationMode.SUBSET`. In subset mode,
Kayan validates keys declared in the local schema and ignores undeclared keys,
which lets several modules consume one shared config file.

The lower-level `DefaultConfigResolver` API defaults to strict validation unless
you pass a `KayanValidationMode` explicitly. That keeps standalone resolver usage
conservative while the Gradle plugin stays friendly to multi-module shared config.

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

Kayan fails the build when any of the following conditions are detected:

- **`flavors` is missing or not an object** — every config file must contain a `flavors` key
  with an object value
- **Unknown keys in strict mode** — a config contains a key not declared in the schema
- **Type mismatches** — a value does not match the declared schema type
- **Unknown custom flavors** — the custom config introduces a flavor missing from the base config
- **Missing required values** — a required key does not resolve for a flavor
- **Blocked overrides** — a custom config sets a key marked `preventOverride`

All validation errors include the source file, key path, and flavor name so you can find
and fix the issue quickly.

For shared root schemas, see [Multi-Module Shared Config](../multi-module-shared-config/).
