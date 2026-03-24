package io.kayan.gradle

@MustBeDocumented
@RequiresOptIn(
    message = "Kayan's Gradle build-time config API is experimental and may change.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalKayanGradleApi
