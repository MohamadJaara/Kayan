# Sample App

This sample is a standalone Compose Multiplatform app that consumes the local Kayan plugin from the
parent repository using `includeBuild("..")`.

It demonstrates:

- applying `io.kayan.config`
- applying Compose Multiplatform with shared UI in `commonMain`
- resolving `default.json` plus `custom-overrides.json`
- resolving a brand override from any file path passed into Gradle
- declaring a consumer-owned schema in `build.gradle.kts`
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
