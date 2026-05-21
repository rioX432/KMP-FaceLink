package io.github.kmpfacelink.model

/**
 * Hand laterality classification.
 *
 * Consumers should handle all three values in `when` expressions for exhaustiveness.
 * [UNKNOWN] is a legitimate production value (not just a defensive fallback) — iOS Vision
 * framework reports `VNChiralityUnknown` when the hand pose is ambiguous.
 */
public enum class Handedness {
    /** The detected hand is a left hand. */
    LEFT,

    /** The detected hand is a right hand. */
    RIGHT,

    /**
     * Handedness could not be determined.
     *
     * Produced when:
     * - **iOS (Vision):** `VNChiralityUnknown` is returned for ambiguous poses.
     * - **Android (MediaPipe):** Handedness label is empty or malformed.
     * - **Wasm (MediaPipe JS):** Handedness label does not map to LEFT or RIGHT.
     *
     * Tip: always include an `UNKNOWN` branch (or `else`) in `when` expressions to
     * avoid compiler errors and gracefully handle indeterminate results.
     */
    UNKNOWN,
}
