---
title: Explaining configuration
description: Inspect resolved values and the source of each winning value.
---

Run `explainKayanConfig` to inspect every key in your module's schema:

```bash
./gradlew explainKayanConfig
./gradlew explainKayanConfig --flavor staging
./gradlew explainKayanConfig --flavor prod --target ios
./gradlew :feature:explainKayanConfig --flavor prod --target jvm
```

The task defaults to `kayan.flavor`. `--flavor` overrides that setting for this
invocation, and also works when the extension has no flavor configured. An
unknown flavor fails. `--target` applies matching target overlays; omitting it
resolves only root defaults and flavor values. There is no extension target
default. As with the resolver, a target with no matching overlay falls back to
the ordinary root and flavor values. Blank selectors fail.

Input files, schema, `configFormat`, and `validationMode` come from `kayan`,
including settings inherited through `inheritFromRoot()`. The task uses the
same [resolution order](../resolution-order/) and validation as generation.
All input sections are parsed and validated, including unselected flavors and
targets: invalid declared values and `preventOverride` violations fail there
too. Required entries must resolve for every base flavor with the selected
target.

Example output:

```text
Kayan configuration: flavor="prod", target="ios"
"api_base_url" = "https://api.example.com"
  source: "/workspace/custom.json"
  layer: custom flavor target overlay
  path: "$.flavors.prod.targets.ios.api_base_url"
"optional_label" = null
  source: "/workspace/default.json"
  layer: base root defaults
  path: "$.optional_label"
"unused_flag" = <absent>
  source: <none>
```

`null` means a nullable key was explicitly set to null in the reported source;
it overrides lower layers. `<absent>` means no participating layer supplied the
key. Values appear in schema declaration order. Strings, keys, file names, and
paths use JSON escaping; lists preserve their order and map keys are sorted.
The report shows resolved raw values, including normalized enum names. Custom
adapters are not loaded or executed.

The task runs without a Kotlin plugin, `packageName`, or `className`. It prints
values to the console on every invocation, supports Gradle's configuration
cache, and reads current input files when it executes. It does not compile
Kotlin or generate Kotlin or schema files.
