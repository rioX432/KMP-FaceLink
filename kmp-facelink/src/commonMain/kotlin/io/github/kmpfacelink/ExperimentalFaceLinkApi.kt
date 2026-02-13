package io.github.kmpfacelink

/**
 * Marks declarations that are part of an experimental FaceLink API.
 *
 * These APIs may change in future releases without a deprecation cycle.
 * You must opt in to use them, either by annotating your usage with
 * `@ExperimentalFaceLinkApi` or by using the compiler argument
 * `-opt-in=io.github.kmpfacelink.ExperimentalFaceLinkApi`.
 */
@RequiresOptIn(
    message = "This API is experimental. It may change or be removed in future releases.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class ExperimentalFaceLinkApi
