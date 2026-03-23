import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
}

fun normalizeVersion(rawVersion: String): String {
    val normalizedVersion = rawVersion.trim()
        .removePrefix("refs/tags/")
        .removePrefix("v")

    require(normalizedVersion.isNotEmpty()) {
        "PUBLISH_VERSION must not be blank."
    }

    return normalizedVersion
}

val publishedGroup = providers.gradleProperty("GROUP")
    .orElse("io.github.mohamadjaara")
    .get()
val publishedVersion = providers.gradleProperty("PUBLISH_VERSION")
    .orElse("0.0.1-SNAPSHOT")
    .map(::normalizeVersion)
    .get()

extra["publishedGroup"] = publishedGroup
extra["publishedVersion"] = publishedVersion

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        apply(plugin = "io.gitlab.arturbosch.detekt")

        tasks.named("detekt").configure {
            dependsOn(
                tasks.matching { task ->
                    task.name.startsWith("detekt") &&
                        task.name != "detekt" &&
                        !task.name.startsWith("detektBaseline") &&
                        task.name != "detektGenerateConfig"
                },
            )
        }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }

    plugins.withId("io.gitlab.arturbosch.detekt") {
        dependencies {
            add("detektPlugins", libs.detekt.formatting)
        }

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            parallel = true
        }

        tasks.withType<Detekt>().configureEach {
            jvmTarget = "11"

            reports {
                html.required.set(true)
                sarif.required.set(true)
                xml.required.set(true)
                txt.required.set(false)
            }
        }
    }
}

tasks.register("codeCoverageReport") {
    group = "verification"
    description = "Generates code coverage reports for all supported modules."
    dependsOn(":gradle-plugin:jacocoTestReport")
}
