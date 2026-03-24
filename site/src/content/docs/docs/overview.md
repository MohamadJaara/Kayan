---
title: Overview
description: What Kayan is and how it works.
---

Kayan is a small, opinionated Kotlin Gradle plugin for layered JSON and YAML config.
It works with **Kotlin Multiplatform**, **JVM**, and **Android** projects.

Kayan generates a typed Kotlin API so your code can read configuration directly
instead of routing values through platform-specific build config.

Kayan is designed for non-sensitive configuration. If a value should stay out
of generated code or normal Gradle configuration, keep it out of Kayan. See
[Security](./security.md) for the trust model and main boundaries.

## How it works

1. You write JSON or YAML config files with a `flavors` object for environment-specific values
2. You declare a schema in your `build.gradle.kts` using the `kayan {}` DSL
3. Kayan merges base config with optional overrides and validates everything at build time
4. A typed Kotlin object is generated that your shared code can import directly

## Merge priority

For a selected flavor, values resolve in this priority order:

1. Custom config flavor value
2. Custom config top-level default value
3. Base config flavor value
4. Base config top-level default value

Deterministic precedence. Strict schema validation. Generated Kotlin source.
