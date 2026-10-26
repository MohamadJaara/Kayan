---
title: Commands
description: Useful Gradle commands for working with Kayan.
---

## Build and test

```bash
# Full build
./gradlew build

# Run plugin tests
./gradlew :gradle-plugin:test
```

## Sample app

```bash
# Run desktop sample
./gradlew -p sample run

# Run web (Wasm) sample
./gradlew -p sample wasmJsBrowserDevelopmentRun

# Generate config source
./gradlew -p sample generateKayanConfig

# Validate every flavor and configured target without generating artifacts
./gradlew -p sample validateKayanConfig

# Export JSON Schema + Markdown
./gradlew -p sample exportKayanSchema

# Explain values and their winning sources
./gradlew -p sample explainKayanConfig
./gradlew -p sample explainKayanConfig --flavor prod --target ios
```

In consuming projects, run `./gradlew validateKayanConfig` for standalone CI
validation, or `./gradlew check` when a check lifecycle is available. See
[Validation](../validation/) for target coverage and required-value behavior.

See [Explaining configuration](../explaining-configuration/) for selectors and output details.

## Docs site

```bash
# Build the Astro/Starlight docs site
npm run build --prefix site
```
