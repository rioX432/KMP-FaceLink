package io.github.kmpfacelink.capture

/**
 * Which soft (non-blocking) check runs for a scenario. Soft failures still keep the take,
 * marked failed=true, and prompt a retry. These scenario checks use the explicitly selected
 * landmark/time proxies; their existence does not generalize a cheek-puff observation to every
 * MediaPipe blendshape or certify expression correctness.
 */
public enum class SoftValidationKind {
    TIME_ONLY,
    BLINK_COUNT,
    SMILE,
    HEAD_TURN,
}
