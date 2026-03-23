---
title: BuildConfig Migration
description: How to migrate from Android BuildConfig to Kayan.
---

Kayan works well as a gradual replacement for Android-only `BuildConfig`.

## Migration steps

1. Inventory the constants that shared code actually reads
2. Move shared values into `default.json`
3. Move flavor-specific values into `flavors`
4. Declare matching schema entries in `kayan { schema { ... } }`
5. Keep generated property names aligned with existing constant names first
6. Swap imports from `BuildConfig` to the generated Kayan object

## Before and after

```kotlin
// Before
val baseUrl = BuildConfig.API_BASE_URL

// After
import sample.generated.SampleConfig

val baseUrl = SampleConfig.API_BASE_URL
```

The generated object lives in shared code, so every platform target can read it directly
without platform-specific `expect`/`actual` wiring.
