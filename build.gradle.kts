import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka) apply false
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

dependencies {
    detektPlugins(project(":detekt-rules"))
}

detekt {
    disableDefaultRuleSets = true
    config.setFrom(layout.projectDirectory.file("config/detekt/no-em-dashes.yml"))
    source.setFrom(
        fileTree(layout.projectDirectory) {
            include("**/*.kt", "**/*.kts")
            exclude(
                "**/.git/**",
                "**/.gradle/**",
                "**/.kotlin/**",
                "**/build/**",
                "**/node_modules/**",
                "**/.astro/**",
                "**/dist/**",
                "site/public/api/**",
            )
        },
    )
}

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        apply(plugin = "dev.detekt")

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
        apply(plugin = "dev.detekt")
    }

    plugins.withId("dev.detekt") {
        dependencies {
            add("detektPlugins", libs.detekt.formatting)
            add("detektPlugins", project(":detekt-rules"))
        }

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(
                rootProject.file("config/detekt/detekt.yml"),
                rootProject.file("config/detekt/no-em-dashes.yml"),
            )
            parallel = true
        }

        val projectDetekt = tasks.named("detekt")
        rootProject.tasks.named("detekt") {
            dependsOn(projectDetekt)
        }

        tasks.withType<Detekt>().configureEach {
            jvmTarget.set("11")

            reports {
                checkstyle.required.set(true)
                html.required.set(true)
                sarif.required.set(true)
                markdown.required.set(false)
            }
        }
    }
}

tasks.register("codeCoverageReport") {
    group = "verification"
    description = "Generates code coverage reports for all supported modules."
    dependsOn(":gradle-plugin:jacocoTestReport")
}
