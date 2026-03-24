---
title: White-Label Setup
description: Build branded apps from one repo using Kayan config overrides.
---

Keep one shared base file in the app repo and resolve one override file per brand at
build time, with environment-specific values inside `flavors`.

The brand file does not need to live in the same repo. Another checkout, object storage
bucket, or raw URL can provide it. The app repo stays single-source and the branded build
is selected by a file path, not by forking.

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
                .file("config/brands/wafflewizard.json")
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
curl -o /tmp/wafflewizard.json \
  https://example.com/mobile-branding/wafflewizard.json

./gradlew generateKayanConfig \
  -PbrandConfigPath=/tmp/wafflewizard.json \
  -PkayanFlavor=prod
```

Or reference a file from another local checkout:

```bash
./gradlew generateKayanConfig \
  -PbrandConfigPath=../branding/bananabeacon.json \
  -PkayanFlavor=staging
```

By the time Kayan runs, the external override has already been materialized somewhere on
disk. Kayan only sees a file path, which keeps the plugin focused on validation, merge
rules, and source generation.
