# Sample App

This sample is a standalone Compose Multiplatform app that consumes the local Kayan plugin from the
parent repository using `includeBuild("..")`.

It demonstrates:

- applying `io.github.mohamadjaara.kayan`
- applying Compose Multiplatform with shared UI in `commonMain`
- resolving `default.json` plus `custom-overrides.json`
- resolving a brand override from any file path passed into Gradle
- declaring a consumer-owned schema in `build.gradle.kts`
- reading resolved config directly in `build.gradle.kts` with `buildValue()`
- selecting a Kotlin theme implementation from `theme_name`
- configuring desktop packaging metadata from `bundle_id` and `brand_name`
- defining a consumer-owned `SupportMatrix` custom type with a build-time adapter in `build-logic`
- generating `sample.generated.SampleConfig`
- using a consumer-owned `Customization.kt` to choose which generated values are shown on screen
- using platform launchers for desktop, web, and iOS

Configured targets:

- JVM desktop
- Web (`wasmJs`)
- iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`)
- macOS (`macosArm64`)

Run the desktop app:

```bash
../gradlew -p sample run
```

Run the web app:

```bash
../gradlew -p sample wasmJsBrowserDevelopmentRun
```

Build the web bundle:

```bash
../gradlew -p sample wasmJsBrowserDistribution
```

Build and test a few sample targets:

```bash
../gradlew -p sample jvmTest macosArm64Test iosSimulatorArm64Test
```

Generate only the config source:

```bash
../gradlew -p sample generateKayanConfig
```

Generate the branding metadata that Gradle packages for the app:

```bash
../gradlew -p sample generateBrandMetadata
```

Compile the app with the selected theme implementation:

```bash
../gradlew -p sample compileKotlinJvm
```

Try a different flavor:

```bash
../gradlew -p sample compileKotlinJvm -PkayanFlavor=dev
```

The sample now uses `buildValue()` for two realistic build-time decisions:

- `bundle_id` and `brand_name` configure Compose Desktop native distribution metadata
- `theme_name` selects which Kotlin file under `sample/themes/<name>/kotlin/` is copied into generated `commonMain` sources

The default sample setup uses `custom-overrides.json`, so `prod` resolves to:

- `brandName=Example App Custom`
- `featureSearchEnabled=true`
- `themeName=aurora`
- generated theme source: `build/generated/theme-source/commonMain/kotlin/sample/SelectedThemePalette.kt`

While `dev` resolves to:

- `brandName=Example App`
- `featureSearchEnabled=true`
- `themeName=sunrise`
- generated theme source: `build/generated/theme-source/commonMain/kotlin/sample/SelectedThemePalette.kt`

The generated metadata file lands at:

- `build/generated/branding/brand-metadata.json`

This shows the difference between using Kayan from generated app code and using the same
resolved values to drive packaging and compiled UI selection inside Gradle configuration.

Generate config for a brand file that lives outside this repo:

```bash
../gradlew -p sample generateKayanConfig \
  -PbrandConfigPath=/absolute/path/to/wafflewizard-brand.json
```

Example no-fork flow with a fetch step before Gradle:

```bash
curl -o /tmp/wafflewizard-brand.json https://example.com/mobile-branding/wafflewizard.json

../gradlew -p sample generateKayanConfig \
  -PbrandConfigPath=/tmp/wafflewizard-brand.json \
  -PkayanFlavor=prod
```

How the sample is wired:

- Kayan only receives a file path through `-PbrandConfigPath`
- that path can point to a file inside this repo or anywhere else on disk
- CI or a wrapper script can fetch the file first from another repo, storage bucket, or internal service
- the app repo stays unchanged while another system decides which brand file to inject

Export the generated JSON Schema and Markdown docs:

```bash
../gradlew -p sample exportKayanSchema
```
