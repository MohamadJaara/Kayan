plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "sample"
version = "0.0.1"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinpoet)
    testImplementation(libs.kotlin.test)
}

dependencyLocking {
    lockAllConfigurations()
}

kotlin {
    jvmToolchain(11)
}
