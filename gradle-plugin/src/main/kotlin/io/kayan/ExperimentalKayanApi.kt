package io.kayan

/** Opt-in marker for core Kayan APIs that are public but not yet considered stable. */
@MustBeDocumented
@RequiresOptIn(
    message = "Kayan's experimental config APIs may change.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalKayanApi
