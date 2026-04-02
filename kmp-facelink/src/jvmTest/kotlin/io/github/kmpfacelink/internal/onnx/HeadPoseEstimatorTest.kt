package io.github.kmpfacelink.internal.onnx

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class HeadPoseEstimatorTest {

    /** Centered symmetric face should produce near-zero yaw and roll. */
    @Test
    fun centeredFaceHasNearZeroYawAndRoll() {
        val landmarks = createSymmetricFace(centerX = 0.5f)
        val result = HeadPoseEstimator.estimate(landmarks)

        assertTrue(abs(result.yaw) < MAX_NEUTRAL_ANGLE, "Yaw ${result.yaw} should be near zero")
        assertTrue(abs(result.roll) < MAX_NEUTRAL_ANGLE, "Roll ${result.roll} should be near zero")
    }

    /** Face shifted right should produce positive yaw. */
    @Test
    fun rightShiftedFaceHasPositiveYaw() {
        val landmarks = createSymmetricFace(centerX = 0.6f)
        // Shift nose tip further right to simulate head turn
        landmarks[NOSE_TIP_IDX * 2] = 0.65f
        val result = HeadPoseEstimator.estimate(landmarks)

        assertTrue(result.yaw > 0f, "Yaw ${result.yaw} should be positive for right-shifted face")
    }

    /** Face shifted left should produce negative yaw. */
    @Test
    fun leftShiftedFaceHasNegativeYaw() {
        val landmarks = createSymmetricFace(centerX = 0.4f)
        landmarks[NOSE_TIP_IDX * 2] = 0.35f
        val result = HeadPoseEstimator.estimate(landmarks)

        assertTrue(result.yaw < 0f, "Yaw ${result.yaw} should be negative for left-shifted face")
    }

    @Test
    fun positionZDecreasesWithLargerFace() {
        val smallFace = createSymmetricFace(centerX = 0.5f, faceWidth = 0.15f)
        val largeFace = createSymmetricFace(centerX = 0.5f, faceWidth = 0.35f)

        val smallResult = HeadPoseEstimator.estimate(smallFace)
        val largeResult = HeadPoseEstimator.estimate(largeFace)

        assertTrue(
            smallResult.positionZ > largeResult.positionZ,
            "Small face Z (${smallResult.positionZ}) should be > large face Z (${largeResult.positionZ})",
        )
    }

    companion object {
        private const val MAX_NEUTRAL_ANGLE = 10f
        private const val NOSE_TIP_IDX = 30
        private const val NUM_LANDMARKS = 66

        /** Create a symmetric face centered at the given x position. */
        @Suppress("LongMethod")
        fun createSymmetricFace(centerX: Float, faceWidth: Float = 0.3f): FloatArray {
            val landmarks = FloatArray(NUM_LANDMARKS * 2)
            val halfW = faceWidth / 2f

            // Jaw (0-16): curved contour from right to left
            for (i in 0..16) {
                val t = i / 16f
                landmarks[i * 2] = centerX - halfW + faceWidth * t
                landmarks[i * 2 + 1] = 0.45f + 0.35f * (1f - (2f * t - 1f) * (2f * t - 1f))
            }
            // Right eyebrow (17-21)
            for (i in 17..21) {
                val t = (i - 17) / 4f
                landmarks[i * 2] = centerX - halfW * 0.7f + halfW * 0.6f * t
                landmarks[i * 2 + 1] = 0.34f
            }
            // Left eyebrow (22-26)
            for (i in 22..26) {
                val t = (i - 22) / 4f
                landmarks[i * 2] = centerX + halfW * 0.1f + halfW * 0.6f * t
                landmarks[i * 2 + 1] = 0.34f
            }
            // Nose (27-35)
            landmarks[27 * 2] = centerX; landmarks[27 * 2 + 1] = 0.40f
            landmarks[28 * 2] = centerX; landmarks[28 * 2 + 1] = 0.45f
            landmarks[29 * 2] = centerX; landmarks[29 * 2 + 1] = 0.50f
            landmarks[30 * 2] = centerX; landmarks[30 * 2 + 1] = 0.55f
            for (i in 31..35) {
                val t = (i - 31) / 4f
                landmarks[i * 2] = centerX - 0.03f + 0.06f * t
                landmarks[i * 2 + 1] = 0.57f
            }
            // Right eye (36-41)
            landmarks[36 * 2] = centerX - halfW * 0.55f; landmarks[36 * 2 + 1] = 0.40f
            landmarks[37 * 2] = centerX - halfW * 0.45f; landmarks[37 * 2 + 1] = 0.38f
            landmarks[38 * 2] = centerX - halfW * 0.35f; landmarks[38 * 2 + 1] = 0.38f
            landmarks[39 * 2] = centerX - halfW * 0.25f; landmarks[39 * 2 + 1] = 0.40f
            landmarks[40 * 2] = centerX - halfW * 0.35f; landmarks[40 * 2 + 1] = 0.42f
            landmarks[41 * 2] = centerX - halfW * 0.45f; landmarks[41 * 2 + 1] = 0.42f
            // Left eye (42-47)
            landmarks[42 * 2] = centerX + halfW * 0.25f; landmarks[42 * 2 + 1] = 0.40f
            landmarks[43 * 2] = centerX + halfW * 0.35f; landmarks[43 * 2 + 1] = 0.38f
            landmarks[44 * 2] = centerX + halfW * 0.45f; landmarks[44 * 2 + 1] = 0.38f
            landmarks[45 * 2] = centerX + halfW * 0.55f; landmarks[45 * 2 + 1] = 0.40f
            landmarks[46 * 2] = centerX + halfW * 0.45f; landmarks[46 * 2 + 1] = 0.42f
            landmarks[47 * 2] = centerX + halfW * 0.35f; landmarks[47 * 2 + 1] = 0.42f
            // Mouth (48-65): simplified
            for (i in 48..65) {
                landmarks[i * 2] = centerX
                landmarks[i * 2 + 1] = 0.68f
            }
            landmarks[48 * 2] = centerX - 0.05f // mouth right
            landmarks[54 * 2] = centerX + 0.05f // mouth left

            return landmarks
        }
    }
}
