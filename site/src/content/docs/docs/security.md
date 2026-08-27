---
title: Security
description: What Kayan is designed to protect, and what stays outside its threat model.
---

Kayan validates config against the declared schema. It does not secure the rest
of the build system.

The trust model is:

- Kayan treats config files as untrusted data.
- Kayan treats the declared schema as trusted project code.
- Kayan treats custom adapters as trusted build code.

By default, the Gradle plugin uses `KayanValidationMode.SUBSET`. It validates
keys declared in the local schema and ignores undeclared keys, which allows
several modules to read one shared file. If one module owns the whole config
file, use
`KayanValidationMode.STRICT`. See [Validation](../validation/) and
[Multi-module shared config](../multi-module-shared-config/) for the tradeoff.

## What Kayan protects

Kayan protects build integrity and generated source. If a config file no longer
matches the schema, the build fails instead of guessing what the author meant.

Kayan does the following:

- validate schema-declared keys instead of guessing what undeclared data means
- reject unknown keys when `KayanValidationMode.STRICT` is enabled
- enforce declared value types and nullability
- require explicit flavor resolution
- reject custom-only flavors that do not exist in the base config
- generate Kotlin literals from parsed values rather than raw config text

For built-in types, config should become data in generated Kotlin, not code.

## What Kayan is not for

Kayan is not a secret-management system.

If a value should not appear in source control, generated Kotlin, schema
artifacts, or normal Gradle configuration, it should not live in Kayan config.
Use your platform's secret-management or environment-specific secure storage
instead.

Kayan also does not defend against hostile build logic. If someone can change
`build.gradle.kts`, buildscript dependencies, or a custom adapter class, they
already control code that runs in the build.

## Custom adapters are a trust boundary

Built-in schema entries are data-driven. Custom adapters are code-driven.

Kayan can validate adapter metadata and report adapter failures with context,
but it cannot make an untrusted adapter safe. A custom `BuildTimeConfigAdapter`
can execute arbitrary logic and render arbitrary Kotlin expressions.

If you use adapters, review them like any other Gradle plugin or build logic.
See [Custom adapters](../custom-adapters/) for the adapter contract.

## `buildValue()` deserves extra care

`buildValue()` is useful because it lets Gradle configuration read the same
resolved values that generated Kotlin will expose later. It also means config
can influence dependency wiring, task inputs, and other build decisions.

Kayan requires schema-declared keys and checked accessors, but the build author
still decides what those values control. Review config like code when
`buildValue()` affects dependencies, packaging, or task wiring.

## Practical guidance

- Do not store secrets in Kayan-managed config.
- Keep config review strict, especially for changes that affect `buildValue()`.
- Use `KayanValidationMode.STRICT` when a single module owns the full config
  file and unexpected keys should fail the build.
- Treat adapter code as trusted code.
- Run config resolution in CI so broken or suspicious changes fail before merge.

The full engineering threat model lives in the repository at
[`THREAT_MODEL.md`](https://github.com/MohamadJaara/Kayan/blob/main/THREAT_MODEL.md).
