import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlinJvm)
    id("java-gradle-plugin")
    jacoco
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = rootProject.extra["publishedGroup"].toString()
version = rootProject.extra["publishedVersion"].toString()

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation(libs.arrow.core)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.snakeyaml)

    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(11)
    explicitApi()
}

tasks.named<Jar>("jar") {
    dependsOn(tasks.named("classes"))
    from(sourceSets.main.get().output)
}

tasks.named("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.register("checkPublicApiDocs") {
    group = "verification"
    description = "Generates Dokka documentation for the published public API surface."
    dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))

    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude(
                "io/kayan/ConfigError*.class",
                "io/kayan/DiagnosticContext.class",
                "io/kayan/KayanError.class",
                "io/kayan/PathSegment*.class",
                "io/kayan/SchemaError*.class",
                "io/kayan/gradle/GenerationError*.class",
                "io/kayan/gradle/KayanGradleError*.class",
                "io/kayan/gradle/PluginConfigurationError*.class",
            )
        }
    )

    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
    }
}

gradlePlugin {
    plugins {
        create("kayanConfigPlugin") {
            id = "io.github.mohamadjaara.kayan"
            implementationClass = "io.kayan.gradle.KayanConfigPlugin"
            displayName = "Kayan Config Plugin"
            description = "Generates a typed BuildConfig-like Kotlin object from Kayan JSON and YAML config files."
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name = "Kayan Gradle Plugin"
        description = "Gradle plugin for generating typed Kayan configuration objects from JSON and YAML."
        inceptionYear = "2026"
        url = "https://github.com/MohamadJaara/Kayan"

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "repo"
            }
        }

        developers {
            developer {
                id = "MohamadJaara"
                name = "Mohamad Jaara"
                url = "https://github.com/MohamadJaara"
            }
        }

        scm {
            url = "https://github.com/MohamadJaara/Kayan"
            connection = "scm:git:git://github.com/MohamadJaara/Kayan.git"
            developerConnection = "scm:git:ssh://git@github.com/MohamadJaara/Kayan.git"
        }
    }
}

extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        artifactId = when {
            artifactId == project.name -> "kayan-gradle-plugin"
            artifactId.startsWith("${project.name}-") -> artifactId.replaceFirst(project.name, "kayan-gradle-plugin")
            else -> artifactId
        }
    }
}
