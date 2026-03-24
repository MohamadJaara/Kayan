@file:OptIn(
    io.kayan.gradle.ExperimentalKayanGradleApi::class,
    org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class,
)

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

abstract class GenerateBrandMetadataTask : DefaultTask() {
    @get:Input
    abstract val brandName: Property<String>

    @get:Input
    abstract val bundleId: Property<String>

    @get:Input
    abstract val flavorName: Property<String>

    @get:Input
    abstract val apiBaseUrl: Property<String>

    @get:Input
    abstract val themeName: Property<String>

    @get:Input
    abstract val featureSearchEnabled: Property<Boolean>

    @get:Input
    abstract val supportLinks: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeMetadata() {
        val metadata = mapOf(
            "brandName" to brandName.get(),
            "bundleId" to bundleId.get(),
            "flavor" to flavorName.get(),
            "apiBaseUrl" to apiBaseUrl.get(),
            "themeName" to themeName.get(),
            "featureFlags" to mapOf("search" to featureSearchEnabled.get()),
            "supportLinks" to supportLinks.get(),
        )

        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)))
    }
}

buildscript {
    dependencies {
        classpath("sample:sample-build-logic:0.0.1")
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    id("io.github.mohamadjaara.kayan")
}

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvm {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }
    wasmJs {
        browser()
        binaries.executable()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "sample.MainKt"
    }
}

val kayanConfigFormat =
    providers.gradleProperty("kayanConfigFormat")
        .map { io.kayan.ConfigFormat.valueOf(it.uppercase()) }
        .orElse(io.kayan.ConfigFormat.JSON)
val defaultBaseConfigPath = kayanConfigFormat.map { format: io.kayan.ConfigFormat ->
    when (format) {
        io.kayan.ConfigFormat.JSON -> layout.projectDirectory.file("default.json").asFile.absolutePath
        io.kayan.ConfigFormat.YAML -> layout.projectDirectory.file("default.yml").asFile.absolutePath
        io.kayan.ConfigFormat.AUTO -> error("The sample does not use AUTO config format by default.")
    }
}
val defaultCustomBrandConfigPath = kayanConfigFormat.map { format: io.kayan.ConfigFormat ->
    when (format) {
        io.kayan.ConfigFormat.JSON -> layout.projectDirectory.file("custom-overrides.json").asFile.absolutePath
        io.kayan.ConfigFormat.YAML -> layout.projectDirectory.file("custom-overrides.yml").asFile.absolutePath
        io.kayan.ConfigFormat.AUTO -> error("The sample does not use AUTO config format by default.")
    }
}
val baseConfigPath = providers.gradleProperty("baseConfigPath").orElse(defaultBaseConfigPath)
val customBrandConfigPath = providers.gradleProperty("brandConfigPath").orElse(defaultCustomBrandConfigPath)

kayan {
    packageName.set("sample.generated")
    flavor.set(providers.gradleProperty("kayanFlavor").orElse("prod"))
    baseConfigFile.set(layout.file(baseConfigPath.map { file(it) }))
    customConfigFile.set(layout.file(customBrandConfigPath.map { file(it) }))
    configFormat.set(kayanConfigFormat)
    className.set("SampleConfig")
    schema {
        string("brand_name", "BRAND_NAME")
        string("bundle_id", "BUNDLE_ID", required = true)
        boolean("onboarding_enabled", "ONBOARDING_ENABLED")
        string("api_base_url", "API_BASE_URL")
        string("theme_name", "THEME_NAME")
        boolean("feature_search_enabled", "FEATURE_SEARCH_ENABLED")
        stringList("support_links", "SUPPORT_LINKS")
        custom(
            jsonKey = "regional_support_links",
            propertyName = "SUPPORT_MATRIX",
            rawKind = io.kayan.ConfigValueKind.STRING_LIST_MAP,
            adapter = "sample.buildlogic.SupportMatrixAdapter",
            required = true,
        )
    }
}

val desktopBundleId = kayan.buildValue("bundle_id").asString()
val desktopBrandName = kayan.buildValue("brand_name").asString()
val selectedThemeName = kayan.buildValue("theme_name").asString()

compose.desktop {
    application {
        nativeDistributions {
            packageName = desktopBundleId
            description = "$desktopBrandName desktop sample"
        }
    }
}

val generateBrandMetadata = tasks.register<GenerateBrandMetadataTask>("generateBrandMetadata") {
    group = "build setup"
    description = "Generates branded metadata for packaging and support tooling."
    brandName.set(kayan.buildValue("brand_name").asStringProvider())
    bundleId.set(kayan.buildValue("bundle_id").asStringProvider())
    flavorName.set(kayan.flavor)
    apiBaseUrl.set(kayan.buildValue("api_base_url").asStringProvider())
    themeName.set(kayan.buildValue("theme_name").asStringProvider())
    featureSearchEnabled.set(kayan.buildValue("feature_search_enabled").asBooleanProvider())
    supportLinks.set(kayan.buildValue("support_links").asStringListProvider())
    outputFile.set(layout.buildDirectory.file("generated/branding/brand-metadata.json"))
}

val syncSelectedThemeSource = tasks.register<Sync>("syncSelectedThemeSource") {
    group = "build setup"
    description = "Copies the Kotlin theme selected by Kayan into generated commonMain sources."
    val themeDirectory = layout.projectDirectory.dir("themes/$selectedThemeName/kotlin")

    from(themeDirectory)
    into(layout.buildDirectory.dir("generated/theme-source/commonMain/kotlin"))

    doFirst {
        check(themeDirectory.asFile.isDirectory) {
            "Theme '$selectedThemeName' was selected by Kayan, but no matching directory exists at ${themeDirectory.asFile}."
        }
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(syncSelectedThemeSource)
}

tasks.named<Copy>("jvmProcessResources") {
    from(generateBrandMetadata.map { it.outputFile }) {
        into("branding")
    }
}
