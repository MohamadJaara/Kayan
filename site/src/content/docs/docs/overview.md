---
title: Overview
description: What Kayan is and how it works.
---

Kayan is a Kotlin Gradle plugin for layered JSON and YAML config. It supports
Kotlin Multiplatform, JVM, and Android projects.

Kayan generates a typed Kotlin API so your code can read configuration directly
instead of routing values through platform-specific build config.

Kayan is designed for non-sensitive configuration. If a value should stay out
of generated code or normal Gradle configuration, keep it out of Kayan. See
[Security](../security/) for the trust model and main boundaries.

## How it works

1. Write a JSON or YAML config file with a `flavors` object for environment-specific values.
2. Declare the schema in `build.gradle.kts` with the `kayan {}` DSL.
3. Kayan merges the base and override files, then validates the declared entries.
4. Kayan generates a typed Kotlin object that shared code can import.

## Merge priority

For a selected flavor, values resolve in this priority order:

1. Custom config flavor value
2. Custom config top-level default value
3. Base config flavor value
4. Base config top-level default value

## Common setups

- Use [Gradle usage](../gradle-usage/) for the main plugin DSL and defaults.
- Use [Target-specific generation](../target-specific-generation/) when KMP
  source sets need different resolved values behind one shared API.
- Use [Multi-module shared config](../multi-module-shared-config/) when several
  modules read one shared config file.
- Use [Build-time config access](../build-time-config/) when Gradle logic needs
  resolved values during configuration.
