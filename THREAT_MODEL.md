# Kayan Threat Model

This document describes the main attack vectors for Kayan and the security
properties the plugin is trying to preserve.

Kayan is a Gradle plugin that reads layered JSON or YAML config files, resolves
them against an explicit schema, and generates Kotlin source and schema
documentation. It is designed for non-secret application and build
configuration, not for secret storage.

## Security Goals

Kayan is trying to protect:

- Build integrity: malformed or hostile config should not silently change the
  meaning of the build without passing through schema validation and explicit
  flavor resolution.
- Generated source safety: config values for built-in types should not be able
  to inject arbitrary Kotlin code into generated sources.
- Predictable resolution: defaults, flavor overrides, and optional custom
  overrides should resolve deterministically and fail loudly on invalid input.
- Developer visibility: unknown keys, missing required values, invalid types,
  and unknown flavors should surface as actionable build failures.
- Schema fidelity: exported JSON Schema and Markdown should reflect the declared
  Kayan schema rather than unvalidated user input.

## Non-Goals

Kayan is not trying to protect:

- Secrets at rest or in generated code. API keys, passwords, tokens, and other
  sensitive values should not be stored in Kayan config files.
- A hostile Gradle build script or hostile plugin dependency. If an attacker
  can change `build.gradle.kts`, plugin configuration, or buildscript
  dependencies, they already have code-execution ability in the build.
- A hostile custom adapter implementation. Custom adapters are trusted build
  code and can execute arbitrary logic when loaded.

## Assets Worth Protecting

- The correctness of generated Kotlin config objects.
- The integrity of Gradle configuration decisions made through `buildValue()`.
- The contents of schema export artifacts.
- CI reliability and the ability to fail fast on invalid config.
- Developer confidence that config changes only affect declared keys and known
  flavors.

## Trust Boundaries

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

## Attack Surfaces And Threats

### 1. Malicious or malformed config files

Attackers or accidental changes may try to:

- introduce unknown keys that look similar to valid ones
- provide the wrong value type for a declared schema entry
- omit required values and rely on silent fallback behavior
- add custom-only flavors that do not exist in the base config
- craft invalid JSON or YAML to trigger confusing parsing behavior

What Kayan is trying to do:

- reject unknown keys instead of ignoring them
- enforce declared types and nullability
- require `flavors` and explicit flavor resolution
- fail when a custom override introduces an unknown flavor
- fail when required values are still missing after resolution

### 2. Kotlin source injection through config values

Because Kayan generates Kotlin source, a natural threat is trying to smuggle
code through config values.

For built-in scalar and collection types, Kayan tries to protect against this by:

- rendering literals from parsed values instead of concatenating raw file text
- escaping string content before writing Kotlin source
- generating typed Kotlin literals for built-in types

Residual risk:

- custom adapters return Kotlin expressions as strings, so adapters are a
  trusted-code boundary. A malicious or compromised adapter can intentionally
  emit arbitrary Kotlin code.

### 3. Abuse of build-time config access

`buildValue()` lets Gradle logic consume resolved Kayan values during the build.
That increases the blast radius of config changes because config can influence
dependency wiring, task inputs, and other build decisions.

What Kayan is trying to do:

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

What Kayan is trying to do:

- validate adapter metadata such as raw kind and Kotlin type shape
- wrap adapter failures with context so they fail as explicit build errors
- keep adapter use explicit in the schema instead of auto-discovering code

Residual risk:

- adapters are fully trusted code. They are outside Kayan's data-validation
  threat boundary and should be reviewed like any other Gradle plugin code.

### 5. Resource exhaustion and build denial of service

Large files, deeply nested structures, or intentionally expensive adapter logic
can slow or break builds.

Current posture:

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

What Kayan is trying to do:

- set expectations in documentation that Kayan is for non-sensitive config

Residual risk:

- the plugin does not detect or redact secrets automatically

## Current Mitigations In The Codebase

- Schema-driven parsing rejects unknown keys and unexpected value kinds.
- Required-after-resolution semantics prevent silent omission of mandatory
  values.
- Built-in string rendering escapes characters that would otherwise alter the
  generated Kotlin source.
- Custom config files are optional, but if present they must resolve against
  the same known flavor set.
- Build-time access is restricted to declared schema keys and checked accessors.
- Errors preserve context such as file path, key, flavor, and adapter class to
  make failures diagnosable.

## Operational Guidance

To keep Kayan in a safe operating envelope:

- do not store secrets in Kayan-managed config
- treat custom adapters as trusted code with the same review bar as any Gradle
  plugin or build logic
- keep write locations for generated artifacts inside expected project or build
  directories
- review config-file changes like code changes, especially when they affect
  `buildValue()` consumers
- run CI on config and schema changes so invalid or suspicious changes fail
  early

## Out Of Scope But Important

These threats matter, but they must be handled outside Kayan itself:

- repository compromise
- malicious third-party Gradle plugins or dependencies
- secret management
- host or CI runner compromise
- Gradle sandboxing or OS-level file system permissions

## Summary

Kayan's main security posture is: treat config as untrusted data, treat schema
and adapters as trusted build code, generate built-in Kotlin literals safely,
and fail closed when config does not match the declared model. The largest
remaining risks are misuse for secrets, trusted-code compromise in build logic
or adapters, and build-time denial of service from expensive inputs.