package io.github.kmpfacelink.capture

/** Structural replay facts used before a participant records the remaining takes. */
public data class CapturePreflightObservation(
    val mode: String,
    val storedFrames: Int,
    val liveResults: Int,
    val joined: Int,
    val faceAgreement: Float,
)

/**
 * Decides only whether the stored input can support later replay.
 *
 * This is deliberately not an expression-quality or accuracy score. It verifies that both replay
 * modes ran, every live result joined back to its stored frame, and replay agreed on face presence.
 */
public object CapturePreflightPolicy {
    public fun passes(observations: List<CapturePreflightObservation>): Boolean {
        if (observations.map(CapturePreflightObservation::mode).toSet() != REQUIRED_MODES) return false
        if (observations.size != REQUIRED_MODES.size) return false
        return observations.all { observation ->
            observation.joined > 0 &&
                observation.joined == observation.liveResults &&
                observation.storedFrames >= observation.liveResults &&
                observation.faceAgreement == 1f
        }
    }

    private val REQUIRED_MODES = setOf("IMAGE", "VIDEO")
}
