package io.kayan.gradle

/** Opt-in marker for Gradle-facing APIs that are public but not yet considered stable. */
@MustBeDocumented
@RequiresOptIn(
    message = "Kayan's Gradle build-time config API is experimental and may change.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalKayanGradleApi
