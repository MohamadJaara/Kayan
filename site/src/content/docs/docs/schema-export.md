---
title: Schema Export
description: Export JSON Schema and Markdown documentation from your Kayan config.
---

Kayan can export both JSON Schema and Markdown docs from the schema declared in
the consuming Gradle project.

## Configuration

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

## Running the export

```bash
./gradlew exportKayanSchema
```

The generated JSON Schema describes raw config types, required keys, the required
`flavors` object, and whole-document unknown-key rejection at both the top level
and inside each flavor. Gradle generation still follows the configured validation
mode, which defaults to `KayanValidationMode.SUBSET`.

That difference matters for shared config files. If several modules consume one
large config file in subset mode, a module-level exported JSON Schema describes
that module's selected keys, not every key other modules may consume. Use a
module that includes the full shared schema when you want an editor schema for
the whole shared document.

The Markdown export uses the same schema entries and includes notes for
`required`, `nullable`, `preventOverride`, enum, and custom adapter entries.
