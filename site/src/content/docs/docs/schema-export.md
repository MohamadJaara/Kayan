---
title: Schema export
description: Export JSON Schema and Markdown documentation from your Kayan config.
---

Kayan can export both JSON Schema and Markdown docs from the schema declared in
the consuming Gradle project.

## Configure the output files

```kotlin
kayan {
    jsonSchemaOutputFile.set(
        layout.projectDirectory.file(
            "config/kayan.schema.json"
        )
    )
    markdownSchemaOutputFile.set(
        layout.projectDirectory.file(
            "docs/config-schema.md"
        )
    )
}
```

## Run the export

```bash
./gradlew exportKayanSchema
```

The generated JSON Schema describes raw config types, required keys, the required
`flavors` object, and whole-document unknown-key rejection at both the top level
and inside each flavor. Gradle generation still follows the configured validation
mode, which defaults to `KayanValidationMode.SUBSET`.

In subset mode, a module-level JSON Schema describes only that module's selected
keys. It does not include keys consumed by other modules in the shared file. To
generate an editor schema for the whole document, export from a module that
includes the full shared schema.

The Markdown export uses the same entries. It records `required`, `nullable`,
and `preventOverride` constraints, along with enum and custom adapter details.
