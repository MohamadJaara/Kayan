package io.kayan.gradle

/** Opt-in marker for source-generation APIs that may evolve without a strict compatibility guarantee. */
@MustBeDocumented
@RequiresOptIn(
    message = "Kayan's experimental source-generation APIs may change.",
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
public annotation class ExperimentalKayanGenerationApi
