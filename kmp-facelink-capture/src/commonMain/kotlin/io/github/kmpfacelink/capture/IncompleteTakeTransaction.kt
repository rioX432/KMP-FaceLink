@file:Suppress(
    // Capture initialization and publication can fail through platform I/O or validation; every
    // throwable must trigger cleanup, while the original throwable remains the diagnostic cause.
    "TooGenericExceptionCaught",
)

package io.github.kmpfacelink.capture

/**
 * Owns an unpublished take directory until [block] has transferred it to an active take.
 *
 * Capture writes are deliberately fail-closed: if validation or any sink constructor throws after
 * the directory was created, the incomplete payload is removed. A cleanup failure is attached to
 * the primary failure, so callers never mistake a storage leak for a successful retry.
 */
internal inline fun <Directory, Result> withIncompleteTakeDirectory(
    create: () -> Directory,
    discard: (Directory) -> Unit,
    block: (Directory) -> Result,
): Result {
    val directory = create()
    return cleanupIncompleteTakeOnFailure(directory, discard) { block(directory) }
}

/** Keeps [directory] only when [block] completes, including a successful atomic publication. */
internal inline fun <Directory, Result> cleanupIncompleteTakeOnFailure(
    directory: Directory,
    discard: (Directory) -> Unit,
    block: () -> Result,
): Result = try {
    block()
} catch (primary: Throwable) {
    try {
        discard(directory)
    } catch (cleanup: Throwable) {
        primary.addSuppressed(cleanup)
    }
    throw primary
}
