---
title: Schema Export
description: Export JSON Schema and Markdown documentation from your Kayan config.
---

Kayan can export both JSON Schema and Markdown docs for the consumer-owned Gradle DSL.

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

The export validates raw JSON types, required keys, the required `flavors` object, and
unknown keys at both the top level and inside each flavor.
