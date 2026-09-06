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

    /**
     * Structural first-take gate for the participant-facing coefficient-only profile. Pixel replay
     * is intentionally absent, so this checks no-pixel semantics plus the complete result-stream
     * contract instead of pretending IMAGE/VIDEO replay is available.
     */
    public fun passesCoefficientOnly(observation: CoefficientOnlyPreflightObservation): Boolean =
        observation.schemaVersion == TakeManifest.SCHEMA_VERSION &&
            observation.profile == CaptureStorageProfile.COEFFICIENTS_ONLY_V1 &&
            observation.pixelSemantics == CapturePixelSemantics.NONE &&
            observation.framesStored == 0 &&
            !observation.framesDirectoryExists &&
            observation.resultRecords > 0 &&
            observation.resultRecords == observation.manifestInferenceResults &&
            observation.analysisFramesAdmitted >= observation.resultRecords &&
            observation.sequenceAndTimestampStrictlyIncreasing &&
            observation.resultStreamHashesMatch
}

/** Public-safe structural observations; contains no coefficient, landmark, transform, or pixel. */
public data class CoefficientOnlyPreflightObservation(
    val schemaVersion: Int,
    val profile: CaptureStorageProfile,
    val pixelSemantics: CapturePixelSemantics,
    val framesStored: Int,
    val framesDirectoryExists: Boolean,
    val analysisFramesAdmitted: Int,
    val manifestInferenceResults: Int,
    val resultRecords: Int,
    val sequenceAndTimestampStrictlyIncreasing: Boolean,
    val resultStreamHashesMatch: Boolean,
)
