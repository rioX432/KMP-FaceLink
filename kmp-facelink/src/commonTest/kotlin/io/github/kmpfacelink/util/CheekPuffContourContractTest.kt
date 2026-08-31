package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.FaceLandmark
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Non-biometric mutation controls for the frozen contour rule's required decision boundaries. */
class CheekPuffContourContractTest {

    @Test
    fun firesForJawBandOutsetOnly() {
        val detector = calibratedDetector()

        val decision = detector.update(frame(jawOutset = 4f), 2.0)

        assertTrue(decision.fired)
        assertTrue(decision.ovalJaw >= CheekPuffContourDetector.OVAL_JAW_MIN)
        assertTrue(decision.mouthWidth <= CheekPuffContourDetector.MOUTH_WIDTH_MAX)
        assertTrue(decision.ovalChin <= CheekPuffContourDetector.OVAL_CHIN_MAX)
    }

    @Test
    fun widenedMouthRejectsTheSameJawSignal() {
        val detector = calibratedDetector()

        val decision = detector.update(frame(jawOutset = 4f, mouthHalfWidth = 23f), 2.0)

        assertFalse(decision.fired)
        assertTrue(decision.mouthWidth > CheekPuffContourDetector.MOUTH_WIDTH_MAX)
    }

    @Test
    fun chinOutsetRejectsTheSameJawSignal() {
        val detector = calibratedDetector()

        val decision = detector.update(frame(jawOutset = 4f, chinOutset = 4f), 2.0)

        assertFalse(decision.fired)
        assertTrue(decision.ovalChin > CheekPuffContourDetector.OVAL_CHIN_MAX)
    }

    private fun calibratedDetector(): CheekPuffContourDetector {
        val detector = CheekPuffContourDetector(IMAGE_SIZE, IMAGE_SIZE)
        repeat(15) { detector.addCalibrationFrame(frame(), it / 30.0) }
        detector.finishCalibration()
        return detector
    }

    /**
     * A deterministic synthetic face-like cloud. It contains no sampled face coordinates and is
     * useful only for guarding the three-condition implementation, not for an accuracy claim.
     */
    private fun frame(
        jawOutset: Float = 0f,
        chinOutset: Float = 0f,
        mouthHalfWidth: Float = 20f,
    ): List<FaceLandmark> {
        val points = MutableList(CheekPuffContourDetector.LANDMARK_COUNT) { index ->
            FaceLandmark(
                x = (200f + (index % 17) * 0.01f) / IMAGE_SIZE,
                y = (100f + (index % 19) * 0.01f) / IMAGE_SIZE,
                relativeDepth = ((index % 7) * 0.01f) / IMAGE_SIZE,
            )
        }

        fun set(index: Int, x: Float, y: Float, z: Float = 0f) {
            points[index] = FaceLandmark(
                x / IMAGE_SIZE.toFloat(),
                y / IMAGE_SIZE.toFloat(),
                z / IMAGE_SIZE.toFloat(),
            )
        }

        // Non-coplanar stable anchors and the fixed mid-line used by alignment.
        set(10, 200f, 50f, 0f)
        set(168, 200f, 80f, 5f)
        set(6, 200f, 105f, -4f)
        set(197, 196f, 115f, 2f)
        set(195, 200f, 125f, 0f)
        set(33, 155f, 100f, -2f)
        set(133, 185f, 105f, 1f)
        set(362, 215f, 105f, 1f)
        set(263, 245f, 100f, -2f)
        set(152, 200f, 250f, 0f)

        set(468, 150f, 105f)
        set(473, 250f, 105f)
        set(61, 200f - mouthHalfWidth, 170f)
        set(291, 200f + mouthHalfWidth, 170f)
        set(13, 200f, 168f)
        set(14, 200f, 172f)

        val oval = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365,
            379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93,
            234, 127, 162, 21, 54, 103, 67, 109,
        )
        oval.forEachIndexed { position, index ->
            if (index == 10 || index == 152) return@forEachIndexed
            val fraction = position.toFloat() / (oval.size - 1).toFloat()
            val y = 50f + 200f * fraction
            val side = if (position < oval.size / 2) 1f else -1f
            val baseOutset = 60f + 20f * kotlin.math.sin(fraction * kotlin.math.PI).toFloat()
            val bandDelta = when {
                fraction >= 0.70f && fraction < 0.85f -> jawOutset
                fraction >= 0.85f -> chinOutset
                else -> 0f
            }
            set(index, 200f + side * (baseOutset + bandDelta), y)
        }
        return points
    }

    private companion object {
        const val IMAGE_SIZE = 400
    }
}
