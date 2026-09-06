@file:Suppress("MagicNumber")
@file:OptIn(io.github.kmpfacelink.ExperimentalFaceLinkApi::class)

package io.github.kmpfacelink.research

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceLandmark
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrozenC2C1ResearchAdapterTest {
    @Test
    fun exactFrozenLipGatedTimesContourFormulaMatchesVerifiedSourceControl() {
        val adapter = readyAdapter()
        val fired = adapter.observe(
            input(
                1_300,
                neutral() + mapOf(
                    BlendShape.MOUTH_ROLL_LOWER to 0.2f,
                    BlendShape.MOUTH_ROLL_UPPER to 0.3f,
                    BlendShape.MOUTH_LEFT to 0.4f,
                    BlendShape.MOUTH_FUNNEL to 0.1f,
                ),
                face(jawOutset = 4f),
            ),
        )
        assertEquals(FrozenResearchReadiness.READY, fired.readiness)
        assertEquals(0.3f, fired.value)
        assertEquals(
            "207b95d493c26a30aa779d7b91eeed9815089465f13009944e1ec7790e37f737",
            FrozenC2C1ResearchAdapter.VERIFIED_ORIGINAL_SOURCE_SHA256,
        )
    }

    @Test
    fun absenceIsNotNumericZeroAndResetRequiresFreshTakeCalibration() {
        val adapter = FrozenC2C1ResearchAdapter()
        val missing = adapter.observe(input(0, neutral().minus(BlendShape.MOUTH_ROLL_LOWER), face()))
        assertEquals(FrozenResearchReadiness.REJECTED, missing.readiness)
        assertNull(missing.value)

        val ready = readyAdapter()
        assertEquals(
            FrozenResearchReadiness.READY,
            ready.observe(input(1_300, neutral(), face())).readiness,
        )
        ready.reset()
        val afterReset = ready.observe(input(0, neutral(), face()))
        assertEquals(FrozenResearchReadiness.NOT_READY, afterReset.readiness)
        assertNull(afterReset.value)
    }

    @Test
    fun takeRelativeWindowDoesNotRebaseWhenFirstJoinedFrameIsNonzero() {
        val adapter = FrozenC2C1ResearchAdapter()
        assertEquals(
            FrozenResearchReadiness.NOT_READY,
            adapter.observe(input(100, neutral(), face())).readiness,
        )
        (250L..950L step 50L).forEach { elapsed ->
            assertEquals(
                FrozenResearchReadiness.NOT_READY,
                adapter.observe(input(elapsed, neutral(), face())).readiness,
            )
        }
        assertEquals(
            FrozenResearchReadiness.NOT_READY,
            adapter.observe(input(1_250, neutral(), face())).readiness,
        )
        assertEquals(
            FrozenResearchReadiness.READY,
            adapter.observe(input(1_251, neutral(), face())).readiness,
        )
    }

    private fun readyAdapter(): FrozenC2C1ResearchAdapter = FrozenC2C1ResearchAdapter().also { adapter ->
        adapter.observe(input(0, neutral(), face()))
        repeat(15) { index ->
            adapter.observe(input(250L + index * 50L, neutral(), face()))
        }
    }

    private fun input(timestamp: Long, values: Map<BlendShape, Float>, points: List<FaceLandmark>) =
        FrozenC2C1Input(timestamp, values, points, IMAGE_SIZE, IMAGE_SIZE)

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

    private fun face(jawOutset: Float = 0f, mouthHalfWidth: Float = 20f): List<FaceLandmark> {
        val points = MutableList(478) { index -> landmark(200f + index % 17 * 0.01f, 100f + index % 19 * 0.01f, 0f) }
        fun set(index: Int, x: Float, y: Float, z: Float = 0f) {
            points[index] = landmark(x, y, z)
        }
        set(10, 200f, 50f)
        set(168, 200f, 80f, 5f)
        set(6, 200f, 105f, -4f)
        set(197, 196f, 115f, 2f)
        set(195, 200f, 125f)
        set(33, 155f, 100f, -2f)
        set(133, 185f, 105f, 1f)
        set(362, 215f, 105f, 1f)
        set(263, 245f, 100f, -2f)
        set(152, 200f, 250f)
        set(468, 150f, 105f)
        set(473, 250f, 105f)
        set(61, 200f - mouthHalfWidth, 170f)
        set(291, 200f + mouthHalfWidth, 170f)
        set(13, 200f, 168f)
        set(14, 200f, 172f)
        FACE_OVAL.forEachIndexed { position, index ->
            if (index == 10 || index == 152) return@forEachIndexed
            val fraction = position.toFloat() / (FACE_OVAL.size - 1)
            val side = if (position < FACE_OVAL.size / 2) 1f else -1f
            val outset = 60f + 20f * sin(fraction * kotlin.math.PI).toFloat()
            val delta = if (fraction >= 0.70f && fraction < 0.85f) jawOutset else 0f
            set(index, 200f + side * (outset + delta), 50f + 200f * fraction)
        }
        return points
    }

    private fun landmark(x: Float, y: Float, z: Float) =
        FaceLandmark(x / IMAGE_SIZE, y / IMAGE_SIZE, z / IMAGE_SIZE)

    companion object {
        private const val IMAGE_SIZE = 400
        private val FACE_OVAL = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365,
            379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93,
            234, 127, 162, 21, 54, 103, 67, 109,
        )
    }
}
