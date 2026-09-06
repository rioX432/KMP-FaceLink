package io.github.kmpfacelink.capture

/** Exclusive process-local ownership of one capture root. Process death releases it implicitly. */
internal interface CaptureRootLease {
    fun release()
}

/** Prevents a second live session from reclaiming the first session's incomplete take. */
internal expect fun acquireCaptureRootLease(canonicalRoot: String): CaptureRootLease
