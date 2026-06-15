---
title: Overview
description: What Kayan is and how it works.
---

Kayan is a small, opinionated Kotlin Gradle plugin for layered JSON and YAML
config. It works with **Kotlin Multiplatform**, **JVM**, and **Android** projects.

Kayan generates a typed Kotlin API so your code can read configuration directly
instead of routing values through platform-specific build config.

Kayan is designed for non-sensitive configuration. If a value should stay out
of generated code or normal Gradle configuration, keep it out of Kayan. See
[Security](../security/) for the trust model and main boundaries.

## How it works

1. You write JSON or YAML config files with a `flavors` object for environment-specific values
2. You declare a schema in your `build.gradle.kts` using the `kayan {}` DSL
3. Kayan merges base config with optional overrides and validates declared schema entries at build time
4. A typed Kotlin object is generated that your shared code can import directly

## Merge priority

For a selected flavor, values resolve in this priority order:

1. Custom config flavor value
2. Custom config top-level default value
3. Base config flavor value
4. Base config top-level default value

## Common setups

- Use [Gradle Usage](../gradle-usage/) for the main plugin DSL and defaults.
- Use [Target-Specific Generation](../target-specific-generation/) when KMP
  source sets need different resolved values behind one shared API.
- Use [Multi-Module Shared Config](../multi-module-shared-config/) when several
  modules read one shared config file.
- Use [Build-Time Config Access](../build-time-config/) when Gradle logic needs
  resolved values during configuration.

Deterministic precedence. Schema-driven validation. Generated Kotlin source.
