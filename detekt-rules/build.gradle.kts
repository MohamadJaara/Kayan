plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
}
