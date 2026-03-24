---
title: Security
description: What Kayan is designed to protect, and what stays outside its threat model.
---

Kayan is intentionally strict about config validation, but it is not trying to
solve every security problem around build tooling.

The short version is:

- Kayan treats config files as untrusted data.
- Kayan treats the declared schema as trusted project code.
- Kayan treats custom adapters as trusted build code.

That split is the most important part of the threat model.

## What Kayan is trying to protect

Kayan is mainly trying to protect build integrity and generated-source safety.
If a config file drifts from the declared schema, the build should fail clearly
instead of guessing what the author meant.

That means Kayan is designed to:

- reject unknown keys instead of silently accepting them
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

That distinction matters. Kayan can validate adapter metadata and surface
adapter failures with useful context, but it cannot make an untrusted adapter
safe. A custom `BuildTimeConfigAdapter` can execute arbitrary logic and can
render arbitrary Kotlin expressions.

If you use adapters, review them like any other Gradle plugin or build logic.

## `buildValue()` deserves extra care

`buildValue()` is useful because it lets Gradle configuration read the same
resolved values that generated Kotlin will expose later. It also means config
can influence dependency wiring, task inputs, and other build decisions.

Kayan keeps this boundary narrow by requiring schema-declared keys and checked
accessors, but it cannot protect a project from its own design choices. If your
build uses `buildValue()` for important decisions, config changes should be
reviewed with the same care as code changes.

## Practical guidance

- Do not store secrets in Kayan-managed config.
- Keep config review strict, especially for changes that affect `buildValue()`.
- Treat adapter code as trusted code.
- Let CI run config resolution so broken or suspicious changes fail early.

The full engineering threat model lives in the repository at
[`THREAT_MODEL.md`](https://github.com/MohamadJaara/Kayan/blob/main/THREAT_MODEL.md).
