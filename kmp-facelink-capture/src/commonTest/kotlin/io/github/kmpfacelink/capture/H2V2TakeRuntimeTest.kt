package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceLandmark
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class H2V2TakeRuntimeTest {
    @Test
    fun eachTakeResetsCalibrationAndThresholdsAndEmitsNothingThrough3000() {
        var modelLoads = 0
        val provider = H2StrictC3bModelProvider(EXPECTED, H2ModelByteLoader {
            modelLoads++
            model().encodeToByteArray()
        }, H2Sha256 { EXPECTED })
        val runtime = H2V2TakeRuntime(H2C3bCandidate(provider), H2C2C1Candidate())
        assertFailsWith<IllegalArgumentException> { runtime.begin(H2ConfirmationContract.blocks.first().id) }

        val first = runtime.begin(H2ConfirmationContract.blocks[1].id)
        calibrate(runtime, first)
        val observed = checkNotNull(runtime.observe(first, 3_001, image(31), neutral()))
        assertTrue(observed.endpointAvailable)
        assertTrue(observed.primary.active)
        runtime.close(first)

        val second = runtime.begin(H2ConfirmationContract.blocks[2].id)
        assertNull(runtime.observe(second, 0, image(0), neutral()))
        assertFailsWith<IllegalArgumentException> { runtime.observe(first, 3_001, image(31), neutral()) }
        calibrate(runtime, second)
        assertTrue(checkNotNull(runtime.observe(second, 3_001, image(31), neutral())).endpointAvailable)
        assertTrue(modelLoads == 1, "take reset must not reload or reparse the immutable model")
    }

    @Test
    fun nonIncreasingTakeTimeFailsEpochAndBeginResetsTheClockGuard() {
        val provider = H2StrictC3bModelProvider(
            EXPECTED,
            H2ModelByteLoader { model().encodeToByteArray() },
            H2Sha256 { EXPECTED },
        )
        val runtime = H2V2TakeRuntime(H2C3bCandidate(provider), H2C2C1Candidate())
        val first = runtime.begin(H2ConfirmationContract.blocks[1].id)
        assertNull(runtime.observe(first, 1_501, image(1), neutral()))
        assertFailsWith<IllegalArgumentException> {
            runtime.observe(first, 1_500, image(2), neutral())
        }
        assertFailsWith<IllegalStateException> {
            runtime.observe(first, 1_502, image(3), neutral())
        }
        runtime.close(first)

        val second = runtime.begin(H2ConfirmationContract.blocks[2].id)
        assertNull(runtime.observe(second, 100, image(4), neutral()))
    }

    private fun calibrate(runtime: H2V2TakeRuntime, epoch: H2TakeEpoch) {
        (250L..1_250L step 50).forEach { elapsed ->
            assertNull(runtime.observe(epoch, elapsed, image((elapsed / 50).toInt()), neutral()))
        }
        (1_501L..2_201L step 50).forEach { elapsed ->
            assertNull(runtime.observe(epoch, elapsed, image((elapsed / 50).toInt()), neutral()))
        }
        assertNull(runtime.observe(epoch, 3_000, image(30), neutral()))
    }

    private fun image(index: Int): H2RgbaLandmarkFrame {
        val rgba = ByteArray(240 * 320 * 4)
        for (pixel in 0 until 240 * 320) {
            rgba[pixel * 4] = ((pixel % 240 + index) % 256).toByte()
            rgba[pixel * 4 + 1] = ((pixel / 240 + index * 3) % 256).toByte()
            rgba[pixel * 4 + 2] = ((pixel + index * 7) % 256).toByte()
            rgba[pixel * 4 + 3] = 0xff.toByte()
        }
        val points = face().toMutableList()
        points[33] = point(40f, 44f); points[133] = points[33]
        points[362] = point(88f, 44f); points[263] = points[362]
        points[1] = point(64f, 72f)
        return H2RgbaLandmarkFrame(
            rgba,
            240,
            320,
            points,
            H2FrameOrientation.FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS,
        )
    }

    private fun face(): List<FaceLandmark> = MutableList(478) { point(120f, 120f) }.also { points ->
        points[10] = point(120f, 30f); points[152] = point(120f, 280f)
        points[168] = point(120f, 70f); points[6] = point(120f, 100f)
        points[197] = point(115f, 115f); points[195] = point(120f, 125f)
        points[61] = point(95f, 190f); points[291] = point(145f, 190f)
        points[13] = point(120f, 187f); points[14] = point(120f, 193f)
        FACE_OVAL.forEachIndexed { position, landmark ->
            if (landmark != 10 && landmark != 152) {
                val fraction = position.toFloat() / (FACE_OVAL.size - 1)
                val side = if (position < FACE_OVAL.size / 2) 1f else -1f
                points[landmark] = point(120f + side * 55f, 30f + 250f * fraction)
            }
        }
    }

    private fun point(x: Float, y: Float) = FaceLandmark(x / 240f, y / 320f, 0f)

    private fun neutral() = mapOf(
        BlendShape.MOUTH_ROLL_LOWER to 0f,
        BlendShape.MOUTH_ROLL_UPPER to 0f,
        BlendShape.MOUTH_LEFT to 0f,
        BlendShape.MOUTH_RIGHT to 0f,
        BlendShape.MOUTH_FUNNEL to 0f,
        BlendShape.JAW_OPEN to 0f,
        BlendShape.MOUTH_SMILE_LEFT to 0f,
        BlendShape.MOUTH_SMILE_RIGHT to 0f,
    )

    private fun model(): String {
        fun vector(size: Int, value: String) = List(size) { value }.joinToString(prefix = "[", postfix = "]")
        val components = List(16) { vector(384, "0") }.joinToString(prefix = "[", postfix = "]")
        return "{\"schema_version\":1,\"recipe\":\"DK_SESSION_B_LEARNED_CHEEK_PATCH_V1\",\"C\":1," +
            "\"scaler_mean\":${vector(384, "0")},\"scaler_scale\":${vector(384, "1")}," +
            "\"pca_mean\":${vector(384, "0")},\"pca_components\":$components," +
            "\"pca_explained_variance\":${vector(16, "1")}," +
            "\"classifier_intercept\":[0],\"classifier_coef\":[${vector(16, "0")}] }"
    }

    companion object {
        private const val EXPECTED = "0000000000000000000000000000000000000000000000000000000000000000"
        private val FACE_OVAL = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365,
            379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93,
            234, 127, 162, 21, 54, 103, 67, 109,
        )
    }
}
