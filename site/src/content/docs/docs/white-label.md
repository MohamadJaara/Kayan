---
title: White-label setup
description: Build branded apps from one repo using Kayan config overrides.
---

Keep one shared base file in the app repo and resolve one override file per brand at
build time, with environment-specific values inside `flavors`.

The brand file can come from another checkout, an object storage bucket, or a raw
URL. Pass its local path to Gradle. You can build each brand from the same app repo
without maintaining a fork.

## Project structure

```text
config/
  default.json
build.gradle.kts
```

You can use `default.yml` instead if the project standardizes on YAML. The important part is that
base and override files use the same configured format.

## Gradle configuration

```kotlin
val brandConfigPath =
    providers.gradleProperty("brandConfigPath")
        .orElse(
            layout.projectDirectory
                .file("config/brands/acme.json")
                .asFile.absolutePath
        )

kayan {
    flavor.set(
        providers.gradleProperty("kayanFlavor")
            .orElse("prod")
    )
    baseConfigFile.set(
        layout.projectDirectory.file("config/default.json")
    )
    customConfigFile.set(
        layout.file(brandConfigPath.map { file(it) })
    )
    // Optional for YAML inputs:
    // configFormat.set(io.kayan.ConfigFormat.YAML)
}
```

## Building a branded variant

Fetch the brand override and pass it as a Gradle property:

```bash
curl -o /tmp/acme.json \
  https://example.com/mobile-branding/acme.json

./gradlew generateKayanConfig \
  -PbrandConfigPath=/tmp/acme.json \
  -PkayanFlavor=prod
```

Or reference a file from another local checkout:

```bash
./gradlew generateKayanConfig \
  -PbrandConfigPath=../branding/partner-a.json \
  -PkayanFlavor=staging
```

Download or copy the external override to disk before Kayan runs. Kayan reads the
file path, validates the contents, applies the merge rules, and generates source.

Use `preventOverride = true` on schema entries that must come only from the base
app config and must not be replaced by a brand override. For shared config across
several modules, see [Multi-module shared config](../multi-module-shared-config/).
