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

# Export JSON Schema + Markdown
./gradlew -p sample exportKayanSchema
```
