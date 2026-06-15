---
title: Schema Types
description: All supported schema types in the Kayan DSL.
---

The following types are available in the `kayan { schema { ... } }` DSL:

| DSL | Generated Kotlin type | Raw config shape |
|------|-------------|-------------|
| `string` | `String` | string |
| `boolean` | `Boolean` | boolean |
| `int` | `Int` | integer |
| `long` | `Long` | integer |
| `double` | `Double` | number |
| `stringMap` | `Map<String, String>` | object with string values |
| `stringList` | `List<String>` | array of strings |
| `stringListMap` | `Map<String, List<String>>` | object with array-of-string values |
| `enumValue` / `enum` | Generated enum | string |
| `custom` | Adapter-defined | raw kind selected by the adapter declaration |

## Modifiers

Every schema entry supports the same constraint flags:

| Flag | Meaning |
| --- | --- |
| `required = true` | The final resolved value must exist for the selected flavor and optional target. |
| `nullable = true` | Config files may explicitly set the value to `null`. |
| `preventOverride = true` | The custom override file cannot set this key; only the base config file may define it. |

`required` and `nullable` cannot both be true for the same entry.

```kotlin
schema {
    string(
        jsonKey = "bundle_id",
        propertyName = "BUNDLE_ID",
        required = true,
    )
    string(
        jsonKey = "internal_distribution_id",
        propertyName = "INTERNAL_DISTRIBUTION_ID",
        preventOverride = true,
    )
}
```

## Names

`jsonKey` is the key Kayan reads from JSON or YAML. `propertyName` is the Kotlin
property generated into the config object.

Generated property names must be valid Kotlin identifiers and must be unique.
JSON keys must be unique and cannot use Kayan's reserved keys: `flavors` and
`targets`.

## Enums

Use `enumValue(...)` or its alias `enum(...)` when config stores a string but
generated Kotlin should expose an enum value:

```kotlin
schema {
    enum(
        jsonKey = "release_stage",
        propertyName = "RELEASE_STAGE",
        enumTypeName = "sample.ReleaseStage",
    )
}
```

Enum config values are normalized into enum constant names during generation.
At Gradle configuration time, `buildValue("release_stage").asEnumName()` returns
the normalized constant name as a `String`.

## Custom types

For types that do not fit the built-in kinds, use `custom(...)` with an adapter:

```kotlin
custom(
    jsonKey = "launch_date",
    propertyName = "LAUNCH_DATE",
    rawKind = io.kayan.ConfigValueKind.STRING,
    adapter = "sample.buildlogic.LaunchDateAdapter",
    required = true,
)
```

The adapter converts the raw validated value into a consumer-owned type and
renders the Kotlin expression used in generated source. See
[Custom Adapters](../custom-adapters/) for the full contract.
