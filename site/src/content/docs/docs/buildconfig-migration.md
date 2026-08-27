---
title: BuildConfig migration
description: How to migrate from Android BuildConfig to Kayan.
---

You can replace Android-only `BuildConfig` one constant at a time. Start with
values already read by shared code.

## Migration steps

1. Inventory the constants that shared code actually reads
2. Move shared values into `default.json` or `default.yml`
3. Move flavor-specific values into `flavors`
4. Declare matching schema entries in `kayan { schema { ... } }`
5. Keep the existing constant names for the first migration pass
6. Swap imports from `BuildConfig` to the generated Kayan object
7. Move Gradle-time decisions to `buildValue()` only when the build script itself
   needs the resolved value

## Before and after

```kotlin
// Before
val baseUrl = BuildConfig.API_BASE_URL

// After
import sample.generated.SampleConfig

val baseUrl = SampleConfig.API_BASE_URL
```

The generated object lives in shared code. Every platform target can read it
without extra `expect` and `actual` declarations.

If different KMP targets need different values behind the same shared API, use
[Target-specific generation](../target-specific-generation/). If the old
`BuildConfig` value drove dependencies, packaging metadata, or source-set wiring,
use [Build-time config access](../build-time-config/).
