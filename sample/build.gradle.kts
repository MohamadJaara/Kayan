@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    dependencies {
        classpath("sample:sample-build-logic:0.0.1")
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    id("io.kayan.config")
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

val customBrandConfigPath =
    providers.gradleProperty("brandConfigPath")
        .orElse(layout.projectDirectory.file("custom-overrides.json").asFile.absolutePath)

kayan {
    packageName.set("sample.generated")
    flavor.set(providers.gradleProperty("kayanFlavor").orElse("prod"))
    baseConfigFile.set(layout.projectDirectory.file("default.json"))
    customConfigFile.set(layout.file(customBrandConfigPath.map { file(it) }))
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
