# AGENTS.md - Kayan Gradle Plugin

Kayan is a Kotlin Gradle plugin that generates typed `BuildConfig`-like Kotlin
objects from layered JSON or YAML config files. It supports Kotlin
Multiplatform, Kotlin/JVM, and Kotlin Android projects. The main production
module is `:gradle-plugin`.

This file is working guidance for coding agents. Prefer durable instructions
over duplicating version numbers that already have a source of truth.

## Source Of Truth

- Use the checked-in Gradle wrapper (`./gradlew`) for all root build work.
  The wrapper version lives in `gradle/wrapper/gradle-wrapper.properties`
  and is currently Gradle 9.1.0.
- Run Gradle with JDK 17 or newer. CI uses JDK 17 for verification and JDK 21
  for publishing.
- The plugin itself is compiled with `jvmToolchain(11)` and must stay Java 11
  compatible for published bytecode.
- Dependency and plugin versions live in `gradle/libs.versions.toml`.
- Build cache and configuration cache are enabled in `gradle.properties`; keep
  task and plugin changes compatible with both unless a task explicitly opts out.
- Do not downgrade or regenerate the wrapper unless the user explicitly asks for
  a wrapper change.

## Commands

Prefer the narrowest command that validates the change, then broaden when the
risk or touched surface warrants it.

```bash
./gradlew :gradle-plugin:test
./gradlew :gradle-plugin:test --tests "io.kayan.DefaultConfigResolverTest"
./gradlew :gradle-plugin:test --tests "io.kayan.gradle.KayanConfigPluginFunctionalTest"
./gradlew :gradle-plugin:validatePlugins
./gradlew :gradle-plugin:checkPublicApiDocs --no-configuration-cache
./gradlew detekt
./gradlew build
```

Sample app tasks run as a separate Gradle build under `sample/`:

```bash
./gradlew -p sample run
./gradlew -p sample wasmJsBrowserDevelopmentRun
./gradlew -p sample generateKayanConfig
./gradlew -p sample exportKayanSchema
```

## Project Map

```text
gradle-plugin/src/main/kotlin/io/kayan/         Core config model, schema, resolver
gradle-plugin/src/main/kotlin/io/kayan/gradle/  Gradle plugin, extensions, tasks, generators
gradle-plugin/src/test/kotlin/io/kayan/         Core unit tests
gradle-plugin/src/test/kotlin/io/kayan/gradle/  Gradle, TestKit, generation tests
gradle-plugin/src/test/resources/golden/        Golden output snapshots
sample/                                         Standalone sample app and build logic
site/                                           Static docs and landing page
config/detekt/detekt.yml                       Detekt and formatting rules
```

Keep core parsing and resolution logic in `io.kayan` free of Gradle API types.
Use `io.kayan.gradle` for Gradle integration, task wiring, providers, and
Gradle-facing exceptions.

## Coding Conventions

- Kotlin style is official Kotlin style with detekt-formatting enforced.
- Public API declarations must use explicit `public`; the module has
  `explicitApi()` enabled.
- Use `internal` for module-scoped implementation details and `private` for
  local helpers.
- Tests use `kotlin.test` imports, not direct JUnit imports.
- Test method names are camelCase descriptive sentences:
  `normalizesTopLevelValuesIntoFlavorResolution`.
- Avoid wildcard imports. Keep imports explicit and sorted in one flat block.
- Keep `if` statements braced.
- Avoid `!!`; prefer safe calls, safe casts, `requireNotNull`, or
  `checkNotNull` depending on whether the null would be user input or a
  programming error.
- Do not edit generated lockfiles unless a dependency or plugin version actually
  changes.

## Error Handling

Kayan favors typed errors internally and throws only at public/API boundaries.

- Model recoverable failures with existing sealed error families such as
  `SchemaError`, `ConfigError`, `PluginConfigurationError`, `GenerationError`,
  and `BuildTimeAccessError`.
- Use Arrow `Either` / `either {}` for parsing, resolution, validation,
  reflection, and file I/O flows that can fail.
- Convert to `ConfigValidationException` or `GradleException` at the boundary
  with the existing `toConfigValidationException()` and `toGradleException()`
  helpers.
- Preserve diagnostic context in errors: source name, flavor, key/path, adapter
  class, file path, operation name, or target name.
- Wrap low-level causes such as serialization, reflection, and I/O failures
  instead of leaking raw exceptions.
- Never silently ignore invalid config or failed generation.

## Gradle Plugin Guidance

- Prefer lazy Gradle APIs (`Provider`, `Property`, task inputs/outputs) over
  eager file reads or value resolution during configuration.
- Keep configuration-cache compatibility in mind for new tasks and value sources.
- Register tasks lazily with `tasks.register` and wire task dependencies through
  providers or explicit inputs/outputs.
- Keep `buildValue()` behavior Gradle-friendly: failures should surface as clear
  `GradleException`s with typed causes behind them.
- When changing source generation, update or add golden tests in
  `gradle-plugin/src/test/resources/golden/`.
- When touching Android or KMP source-set wiring, add focused functional coverage
  because those integrations are easy to regress.

## Test And Verification Expectations

- Core resolver/model changes: run the relevant `io.kayan.*Test` class or method.
- Gradle plugin wiring changes: run the relevant `io.kayan.gradle.*Test`, and
  prefer functional TestKit coverage for real Gradle behavior.
- Generated Kotlin changes: run `KayanGeneratorGoldenTest` and inspect golden
  diffs intentionally.
- Public API changes: run `:gradle-plugin:checkPublicApiDocs --no-configuration-cache`.
- Before handing off broad production changes, run `./gradlew detekt` when time
  allows.

## CI Notes

Pull requests and pushes to `main` validate the wrapper, run detekt, run
`:gradle-plugin:test`, run `:gradle-plugin:validatePlugins`, and build public
API docs. Release tags run the verification workflow first, then publish with
JDK 21.
