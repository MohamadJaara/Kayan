plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "sample"
version = "0.0.1"

repositories {
    google()
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
}

kotlin {
    jvmToolchain(11)
}
