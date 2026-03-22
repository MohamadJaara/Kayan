# AGENTS.md — Kayan Gradle Plugin

Kayan is a Kotlin Multiplatform Gradle plugin that generates typed `BuildConfig`-like
Kotlin objects from layered JSON config files. The main module is `:gradle-plugin`.

## Build System

- **Gradle 8.14.3** with Kotlin DSL (`build.gradle.kts`)
- **Kotlin 2.3.20**, JVM toolchain target **JDK 11**
- Configuration cache and build cache are both enabled
- Version catalog at `gradle/libs.versions.toml`

## Build Commands

```bash
./gradlew build                          # Full build
./gradlew :gradle-plugin:test            # Run all plugin tests
./gradlew :gradle-plugin:validatePlugins # Validate Gradle plugin descriptors
./gradlew detekt                         # Run detekt static analysis (all subprojects)
```

### Running a Single Test

```bash
# Single test class
./gradlew :gradle-plugin:test --tests "io.kayan.DefaultConfigResolverTest"

# Single test method
./gradlew :gradle-plugin:test --tests "io.kayan.DefaultConfigResolverTest.parsesRicherTypes"

# Functional tests (GradleTestKit-based)
./gradlew :gradle-plugin:test --tests "io.kayan.gradle.KayanConfigPluginFunctionalTest"

# Golden/snapshot tests
./gradlew :gradle-plugin:test --tests "io.kayan.gradle.KayanGeneratorGoldenTest"
```

### Sample App (separate Gradle build in `sample/`)

```bash
./gradlew -p sample run                           # Desktop
./gradlew -p sample wasmJsBrowserDevelopmentRun    # Web
./gradlew -p sample generateKayanConfig            # Generate config source
./gradlew -p sample exportKayanSchema              # Export JSON Schema + Markdown
```

## Test Framework

Tests use **`kotlin.test`** (not JUnit directly). Use:
- `@Test` from `kotlin.test.Test`
- `assertEquals`, `assertFailsWith`, `assertNull`, `assertTrue` from `kotlin.test`
- `assertMessageContains` custom helper for error message assertions
- GradleTestKit (`GradleRunner`) for functional tests
- Golden files in `gradle-plugin/src/test/resources/golden/`

Test method names are **camelCase descriptive sentences** (no underscores):
```kotlin
@Test fun normalizesTopLevelValuesIntoFlavorResolution() { ... }
```

## Linting & Static Analysis

**Detekt** with `detekt-formatting` (ktlint-based). Zero tolerance: `maxIssues: 0`,
`warningsAsErrors: true`. Config at `config/detekt/detekt.yml`.

Run before submitting: `./gradlew detekt`

## Code Style Rules

### Formatting
- `kotlin.code.style=official` (Kotlin official style)
- ktlint formatting enforced via detekt-formatting plugin
- No blank line before closing brace
- No blank lines inside parameter/argument lists
- No consecutive blank lines
- Braces required on `if` statements (`BracesOnIfStatements`)

### Imports
- **No wildcard imports** — always use explicit, fully-qualified single imports
- Sorted alphabetically in a single flat block (no blank-line separators between groups)
- Standard library first, then third-party, but no enforced blank lines between

### Naming Conventions
| Element              | Convention             | Example                                    |
|----------------------|------------------------|--------------------------------------------|
| Packages             | lowercase dot-separated | `io.kayan.gradle`                         |
| Classes/Interfaces   | PascalCase             | `KayanConfigPlugin`, `ConfigValue`         |
| Objects              | PascalCase             | `KayanConfigGenerator`                     |
| Functions            | camelCase              | `resolveConfig()`, `parseInternal()`       |
| Local variables      | camelCase              | `flavorName`, `configJson`                 |
| Constants            | SCREAMING_SNAKE_CASE   | `FLAVORS_KEY`, `MAX_SUGGESTIONS`           |
| Enum entries         | SCREAMING_SNAKE_CASE   | `RELEASE`, `INTERNAL`                      |
| Boolean properties   | Prefix: `is`, `has`, `are` | `isEnabled`, `hasAccess`              |
| Test classes         | Suffix `Test`          | `DefaultConfigResolverTest`                |
| Gradle tasks         | Suffix `Task`          | `GenerateKayanConfigTask`                  |
| Adapters             | Suffix `Adapter`       | `BuildTimeConfigAdapter`                   |

### Visibility
- **Explicit `public`** on all public API declarations (classes, interfaces, functions, properties)
- `internal` for module-scoped implementation details
- `private` for truly private members
- In test classes, default visibility (no explicit `public`)

### Nullability & Types
- Non-nullable by default; nullable types explicitly marked with `?`
- Avoid `!!` — use `?.let {}`, `?.takeIf {}`, safe casts `as?`, or `orEmpty()`
- Use `requireNotNull()` / `checkNotNull()` when null is a programming error
- Sealed interfaces/classes for type-safe hierarchies (e.g., `ConfigValue`)

## Error Handling Patterns

1. **`ConfigValidationException`** for config parsing/resolution errors — always include
   diagnostic context (source name, flavor, JSON path)
2. **`require()`** for precondition checks with descriptive messages
3. **`GradleException`** for Gradle-level plugin configuration failures
4. **`runCatching` + `getOrElse`** for recoverable operations with context wrapping
5. **`runCatching` + `recoverCatching`** for fallback chains (e.g., reflection)
6. **Wrap lower-level exceptions** — catch `SerializationException` etc. and rethrow as
   `ConfigValidationException` with added context
7. **Never swallow exceptions silently**

## Project Structure

```
gradle-plugin/src/main/kotlin/io/kayan/         # Core models & resolver
gradle-plugin/src/main/kotlin/io/kayan/gradle/   # Gradle plugin, tasks, generators
gradle-plugin/src/test/kotlin/io/kayan/          # Unit tests
gradle-plugin/src/test/kotlin/io/kayan/gradle/   # Functional & golden tests
gradle-plugin/src/test/resources/golden/          # Golden snapshot files
config/detekt/detekt.yml                          # Detekt configuration
sample/                                           # Standalone sample app (separate build)
site/                                             # Static landing page (GitHub Pages)
```

## CI (GitHub Actions)

- **On push/PR to main:** `detekt` then matrix of `test` + `validatePlugins` (JDK 17)
- **On release:** Verify then publish to Maven Central (JDK 21)
- All CI runs validate the Gradle wrapper first
