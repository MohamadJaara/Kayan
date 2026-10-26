import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

plugins {
    alias(libs.plugins.detekt) apply false
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

val checkNoEmDashes = tasks.register("checkNoEmDashes") {
    group = "verification"
    description = "Rejects em dashes in Kotlin source files, comments, and strings."

    val repositoryDirectory = layout.projectDirectory.asFile
    val kotlinSources = fileTree(repositoryDirectory) {
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
    }
    inputs.files(kotlinSources)
        .withPropertyName("kotlinSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    doLast {
        val violations = kotlinSources.files.sortedBy { it.path }.flatMap { file ->
            val path = file.relativeTo(repositoryDirectory).invariantSeparatorsPath
            file.readLines(Charsets.UTF_8).flatMapIndexed { lineIndex, line ->
                line.mapIndexedNotNull { columnIndex, character ->
                    if (character == '\u2014') {
                        "$path:${lineIndex + 1}:${columnIndex + 1}: " +
                            "Em dashes (U+2014) are not allowed. Use a hyphen or rewrite the sentence."
                    } else {
                        null
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString("\n"))
        }
    }
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
        }

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            parallel = true
        }

        tasks.withType<Detekt>().configureEach {
            dependsOn(checkNoEmDashes)
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
