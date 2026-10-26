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

## Validate configuration in CI

```bash
./gradlew validateKayanConfig
```

`validateKayanConfig` validates the configured schema and base/custom JSON or YAML
files using `configFormat` and `validationMode`. It checks every base flavor,
regardless of `kayan.flavor`, including required values, types, protected overrides,
unknown custom flavors and targets, and the selected unknown-key policy. Failures
stop the build with the existing resolver diagnostics; success reports the number
of flavors, targets, and validation mode.

The task belongs to the `verification` group and runs as part of `check` whenever
that lifecycle task exists, including when another plugin registers it later.
It can also run with only the Kayan plugin applied: no Kotlin or Android plugin,
`packageName`, `className`, or selected `flavor` is required. It writes no Kotlin
source or schema artifacts and has no dependency on generation or schema export.
Validation runs on every invocation, including when the configuration cache is reused.

With `targets(...)` or `targetSourceSets { ... }` mappings, required values must
resolve for every base flavor and every mapped target. Required values may come
entirely from target overlays; a separate base-only required-value check is not
performed. A mapped target without its own overlay inherits the ordinary base and
custom values, following the resolver's normal precedence. All sections are still
parsed and checked for types, unknown keys, unknown custom targets, and protected
overrides, even if their target has no generation mapping.

Without generation mappings, the task resolves every target declared anywhere in
the base file, across every base flavor. This allows standalone CI validation of
target-aware files. Custom files may only use targets declared in the base file.
If the base declares no targets, the task resolves each flavor without target
overlays. These rules apply only to validation; source generation is unchanged.

For shared root schemas, see [Multi-module shared config](../multi-module-shared-config/).
