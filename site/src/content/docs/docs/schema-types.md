---
title: Schema Types
description: All supported schema types in the Kayan DSL.
---

The following types are available in the `kayan { schema { ... } }` DSL:

| Type | Kotlin type | Description |
|------|-------------|-------------|
| `string` | `String` | Plain text value |
| `boolean` | `Boolean` | `true` or `false` |
| `int` | `Int` | 32-bit integer |
| `long` | `Long` | 64-bit integer |
| `double` | `Double` | Floating-point number |
| `stringMap` | `Map<String, String>` | Key-value pairs |
| `stringList` | `List<String>` | List of strings |
| `stringListMap` | `Map<String, List<String>>` | Map of string to list of strings |
| `enumValue` | Generated enum | Normalized enum generation |
| `custom` | Consumer-defined | Adapter-based custom types |

## Modifiers

Use `nullable = true` to allow explicit `null` in JSON. Use `required = true` when
the final resolved value must always exist.

## Custom types

For types that don't fit the built-in kinds, use the `custom` declaration with an adapter:

```kotlin
custom(
    jsonKey = "launch_date",
    propertyName = "LAUNCH_DATE",
    rawKind = io.kayan.ConfigValueKind.STRING,
    adapter = "sample.buildlogic.LaunchDateAdapter",
    required = true,
)
```

The adapter class must implement the conversion from the raw JSON value kind to your target type.
