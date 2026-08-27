# Kayan threat model

This document describes the main attack vectors for Kayan and the security
properties the plugin preserves.

Kayan is a Gradle plugin that reads layered JSON or YAML config files, resolves
them against an explicit schema, and generates Kotlin source and schema
documentation. It is designed for non-secret application and build
configuration, not for secret storage.

## Security goals

Kayan protects:

- Build integrity. Malformed or hostile config must not silently change the
  meaning of the build without passing through schema validation and explicit
  flavor resolution.
- Generated source safety. Config values for built-in types must not be able
  to inject arbitrary Kotlin code into generated sources.
- Predictable resolution. Defaults, flavor overrides, and optional custom
  overrides should resolve deterministically and fail loudly on invalid input.
- Useful diagnostics. Missing required values, invalid types, unknown
  flavors, and unknown keys in strict validation mode should surface as
  build failures that identify the source and key path.
- Schema fidelity. Exported JSON Schema and Markdown should reflect the declared
  Kayan schema rather than unvalidated user input.

## Non-goals

Kayan does not protect:

- Secrets at rest or in generated code. API keys, passwords, tokens, and other
  sensitive values should not be stored in Kayan config files.
- A hostile Gradle build script or hostile plugin dependency. If an attacker
  can change `build.gradle.kts`, plugin configuration, or buildscript
  dependencies, they already have code-execution ability in the build.
- A hostile custom adapter implementation. Custom adapters are trusted build
  code and can execute arbitrary logic when loaded.

## Assets worth protecting

- The correctness of generated Kotlin config objects.
- The integrity of Gradle configuration decisions made through `buildValue()`.
- The contents of schema export artifacts.
- CI reliability and the ability to fail fast on invalid config.
- The rule that config changes only affect declared keys and known flavors.

## Trust boundaries

The main trust boundaries are:

- Config files such as `default.json`, `default.yml`, and optional custom
  override files. These are treated as untrusted input and must be parsed and
  validated.
- The declared Kayan schema in the Gradle build. This is trusted project code.
- Custom adapter classes loaded from the buildscript classpath. These are
  trusted code, not untrusted data.
- Generated outputs in `build/generated/...` and configured schema export
  locations. These are derived artifacts and should not become a path for
  silent corruption or confusing behavior.

## Attack surfaces and threats

### 1. Malicious or malformed config files

Attackers or accidental changes may try to:

- introduce undeclared keys that look similar to valid ones
- provide the wrong value type for a declared schema entry
- omit required values and rely on silent fallback behavior
- add custom-only flavors that do not exist in the base config
- craft invalid JSON or YAML to trigger confusing parsing behavior

Kayan responds by:

- reject unknown keys in `KayanValidationMode.STRICT`
- ignore undeclared keys in the Gradle plugin's default
  `KayanValidationMode.SUBSET` while still validating declared keys
- enforce declared types and nullability
- require `flavors` and explicit flavor resolution
- fail when a custom override introduces an unknown flavor
- fail when required values are still missing after resolution

### 2. Kotlin source injection through config values

Because Kayan generates Kotlin source, a config value may try to smuggle code
into the output.

For built-in scalar and collection types, Kayan prevents injection by:

- rendering literals from parsed values instead of concatenating raw file text
- escaping string content before writing Kotlin source
- generating typed Kotlin literals for built-in types

Residual risk:

- custom adapters return Kotlin expressions as strings, so adapters are a
  trusted-code boundary. A malicious or compromised adapter can intentionally
  emit arbitrary Kotlin code.

### 3. Abuse of build-time config access

`buildValue()` lets Gradle logic consume resolved Kayan values during the build.
Config changes can therefore affect dependency wiring, task inputs, and other
build decisions.

Kayan responds by:

- restrict lookups to keys declared in the schema
- preserve the resolved value kind and validate requested accessor types
- fail loudly on missing or null values when callers ask for non-null access

Residual risk:

- if trusted project code uses `buildValue()` to drive sensitive build logic,
  then anyone who can change the config can influence that logic within the
  schema the build author allowed.

### 4. Custom adapter loading and reflection

Kayan supports custom `BuildTimeConfigAdapter` implementations and reflective
adapter loading from the buildscript classpath.

Threats:

- malicious adapter code executes during generation
- adapter parse or render methods throw, hang, or consume excessive resources
- adapter output does not match the declared raw kind or Kotlin type

Kayan responds by:

- validate adapter metadata such as raw kind and Kotlin type shape
- wrap adapter failures with context so they fail as explicit build errors
- keep adapter use explicit in the schema instead of auto-discovering code

Residual risk:

- adapters are fully trusted code. They are outside Kayan's data-validation
  threat boundary and should be reviewed like any other Gradle plugin code.

### 5. Resource exhaustion and build denial of service

Large files, deeply nested structures, or intentionally expensive adapter logic
can slow or break builds.

Current behavior:

- Kayan fails on parse, schema, and resolution errors instead of attempting to
  continue with partial state
- there are no hard size or complexity limits on config files or adapter work

Residual risk:

- very large inputs or expensive adapters can still create build-time denial of
  service

### 6. Accidental secret exposure

Even without an external attacker, teams can misuse Kayan by placing secrets in
files that are checked into source control, exported into schema docs, exposed
through generated Kotlin, or surfaced to Gradle build logic.

Kayan responds by:

- set expectations in documentation that Kayan is for non-sensitive config

Residual risk:

- the plugin does not detect or redact secrets automatically

## Current mitigations in the codebase

- Schema-driven parsing rejects unknown keys in strict validation mode and
  rejects unexpected value kinds for declared keys.
- Required-after-resolution semantics prevent silent omission of mandatory
  values.
- Built-in string rendering escapes characters that would otherwise alter the
  generated Kotlin source.
- Custom config files are optional, but if present they must resolve against
  the same known flavor set.
- Build-time access is restricted to declared schema keys and checked accessors.
- Errors preserve context such as file path, key, flavor, and adapter class to
  make failures diagnosable.

## Operational guidance

Projects using Kayan should:

- keep secrets out of Kayan-managed config
- use `KayanValidationMode.STRICT` when a module owns the whole config file and
  undeclared keys should fail the build
- treat custom adapters as trusted code with the same review bar as any Gradle
  plugin or build logic
- keep write locations for generated artifacts inside expected project or build
  directories
- review config-file changes like code changes, especially when they affect
  `buildValue()` consumers
- run CI on config and schema changes so invalid or suspicious changes fail
  early

## Outside Kayan's scope

Kayan cannot address:

- repository compromise
- malicious third-party Gradle plugins or dependencies
- secret management
- host or CI runner compromise
- Gradle sandboxing or OS-level file system permissions

## Summary

Kayan treats config as untrusted data and treats the schema and adapters as
trusted build code. It renders built-in Kotlin literals from parsed values and
fails when config does not match the declared model. The main remaining risks
are secret exposure, compromised build logic or adapters, and denial of service
from large inputs or expensive adapters.
