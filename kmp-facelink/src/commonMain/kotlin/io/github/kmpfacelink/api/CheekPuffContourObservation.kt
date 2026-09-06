package io.github.kmpfacelink.api

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.CheekPuffContourDetector

/**
 * One frame of diagnostic output from the frozen contour-based cheek-puff rule.
 *
 * This is an independent experimental observation. It does not replace or modify the canonical
 * `cheekPuff` blend-shape coefficient and is not connected to the default tracking route.
 */
@ExperimentalFaceLinkApi
public data class CheekPuffContourObservation(
    public val fired: Boolean,
    public val mouthWidthDelta: Double,
    public val ovalJawDelta: Double,
    public val ovalChinDelta: Double,
)

/**
 * Diagnostic session for observing the frozen contour-based cheek-puff rule on live landmarks.
 *
 * Call [addNeutralFrame] with at least [MINIMUM_NEUTRAL_FRAMES] neutral frames, then call
 * [finishNeutralCalibration]. After that, [observe] returns the independent rule result and its
 * three baseline-relative features. The caller owns any pre-calibration settling period.
 */
@ExperimentalFaceLinkApi
public class CheekPuffContourObservationSession(
    imageWidth: Int,
    imageHeight: Int,
) {
    private val detector = CheekPuffContourDetector(imageWidth, imageHeight)

    public var neutralFrameCount: Int = 0
        private set

    public var isReady: Boolean = false
        private set

    public fun addNeutralFrame(landmarks: List<FaceLandmark>, timestampMs: Long) {
        check(!isReady) { "neutral calibration is already finished" }
        detector.addCalibrationFrame(landmarks, timestampMs / MILLIS_PER_SECOND)
        neutralFrameCount += 1
    }

    public fun finishNeutralCalibration() {
        check(!isReady) { "neutral calibration is already finished" }
        detector.finishCalibration()
        isReady = true
    }

    public fun observe(
        landmarks: List<FaceLandmark>,
        timestampMs: Long,
    ): CheekPuffContourObservation {
        check(isReady) { "finishNeutralCalibration() has not been called" }
        val decision = detector.update(landmarks, timestampMs / MILLIS_PER_SECOND)
        return CheekPuffContourObservation(
            fired = decision.fired,
            mouthWidthDelta = decision.mouthWidth,
            ovalJawDelta = decision.ovalJaw,
            ovalChinDelta = decision.ovalChin,
        )
    }

    public companion object {
        public const val LANDMARK_COUNT: Int = CheekPuffContourDetector.LANDMARK_COUNT
        public const val MINIMUM_NEUTRAL_FRAMES: Int = 15
        public const val RECOMMENDED_NEUTRAL_FRAMES: Int = 45
        public const val MOUTH_WIDTH_MAXIMUM: Double = CheekPuffContourDetector.MOUTH_WIDTH_MAX
        public const val OVAL_JAW_MINIMUM: Double = CheekPuffContourDetector.OVAL_JAW_MIN
        public const val OVAL_CHIN_MAXIMUM: Double = CheekPuffContourDetector.OVAL_CHIN_MAX

        private const val MILLIS_PER_SECOND: Double = 1_000.0
    }
}
