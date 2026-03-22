plugins {
    kotlin("jvm") version "2.3.20"
}

group = "sample"
version = "0.0.1"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
}
