@file:Suppress("MagicNumber")

package io.github.kmpfacelink.research

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationProducerFactory
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.CandidateReadiness
import io.github.kmpfacelink.model.FaceLandmark
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalFaceLinkApi::class)
class CheekSignalCandidateFactoriesTest {
    @Test
    fun exactThreeFormulaFamilyUsesOnlyFrozenChannels() {
        val session = readySession(CheekSignalCandidateFactories.c2LipOnly())
        val receipt = session.observe(
            input(
                timestampMs = 1300L,
                values = neutralValues() + mapOf(
                    BlendShape.MOUTH_ROLL_LOWER to 0.2f,
                    BlendShape.MOUTH_ROLL_UPPER to 0.3f,
                    BlendShape.MOUTH_LEFT to 0.4f,
                    BlendShape.MOUTH_FUNNEL to 0.1f,
                ),
            ),
        )
        val values = receipt.alternatives.associate { it.producerId to it.value }
        assertEquals(0.2f, values.getValue("research:c2-roll-lower/v1"))
        assertEquals(0.3f, values.getValue("research:c2-roll-max/v1"))
        assertEquals(0.3f, values.getValue("research:c2-lip-gated/v1"))
        assertEquals(0f, receipt.selected.getValue(BlendShape.CHEEK_PUFF))
    }

    @Test
    fun mouthPuckerCannotChangeAnyC2Value() {
        val low = readySession(CheekSignalCandidateFactories.c2LipOnly()).observe(
            input(1300L, neutralValues() + (BlendShape.MOUTH_PUCKER to 0f)),
        )
        val high = readySession(CheekSignalCandidateFactories.c2LipOnly()).observe(
            input(1300L, neutralValues() + (BlendShape.MOUTH_PUCKER to 1f)),
        )
        assertEquals(low.alternatives.map { it.value }, high.alternatives.map { it.value })
    }

    @Test
    fun missingInputIsAbsentRatherThanZero() {
        val session = readySession(CheekSignalCandidateFactories.c2LipOnly())
        val values = neutralValues().toMutableMap().also { it.remove(BlendShape.MOUTH_ROLL_LOWER) }
        val receipt = session.observe(input(1300L, values))
        receipt.alternatives.forEach {
            assertNull(it.value)
            assertEquals(CandidateReadiness.MISSING_INPUT, it.readiness)
        }
    }

    @Test
    fun insufficientOpeningNeutralFramesRejectTheEpoch() {
        val session = CandidateObservationSession(CheekSignalCandidateFactories.c2LipOnly())
        session.observe(input(0L))
        repeat(14) { index -> session.observe(input(250L + index * 50L)) }
        val receipt = session.observe(input(1300L))
        receipt.alternatives.forEach {
            assertNull(it.value)
            assertEquals(CandidateReadiness.REJECTED, it.readiness)
            assertEquals("insufficient_opening_neutral_frames", it.reason)
        }
    }

    @Test
    fun frozenContourInterventionDistinguishesFalseFromAbsence() {
        val factories = CheekSignalCandidateFactories.c2LipPlusFrozenContour()
        val session = readySession(factories, landmarks = face())
        val values = neutralValues() + (BlendShape.MOUTH_ROLL_LOWER to 0.3f)

        val fired = session.observe(input(1300L, values, face(jawOutset = 4f)))
        assertTrue(fired.alternatives.all { it.readiness == CandidateReadiness.READY })
        assertTrue(fired.alternatives.all { checkNotNull(it.value) > 0f })

        val rejected = session.observe(input(1400L, values, face(jawOutset = 4f, mouthHalfWidth = 23f)))
        assertTrue(rejected.alternatives.all { it.readiness == CandidateReadiness.READY })
        assertTrue(rejected.alternatives.all { it.value == 0f })

        val missing = CandidateObservationSession(factories).observe(input(0L, values, emptyList()))
        assertTrue(missing.alternatives.all { it.value == null })
        assertTrue(missing.alternatives.all { it.readiness == CandidateReadiness.MISSING_INPUT })
    }

    @Test
    fun factoryIdentityKeepsC1SeparateFromGenericGeometry() {
        val lipIds = CheekSignalCandidateFactories.c2LipOnly().map { it.descriptor.producerId }
        val combined = CheekSignalCandidateFactories.c2LipPlusFrozenContour()
        assertEquals(3, lipIds.size)
        assertTrue(combined.all { it.descriptor.producerId.endsWith("+c1-frozen-contour/v1") })
        assertTrue(combined.none { "landmark-geometry" in it.descriptor.producerId })
        assertTrue(combined.all { it.descriptor.proposedTarget == BlendShape.CHEEK_PUFF })
    }

    private fun readySession(
        factories: List<CandidateObservationProducerFactory>,
        landmarks: List<FaceLandmark> = emptyList(),
    ): CandidateObservationSession {
        val session = CandidateObservationSession(factories)
        session.observe(input(0L, landmarks = landmarks))
        repeat(15) { index -> session.observe(input(250L + index * 50L, landmarks = landmarks)) }
        return session
    }

    private fun input(
        timestampMs: Long,
        values: Map<BlendShape, Float> = neutralValues(),
        landmarks: List<FaceLandmark> = emptyList(),
    ): CandidateObservationInput = CandidateObservationInput(
        identity = CandidateInputIdentity(1u, 0u, faceIndex = 0, timestampMs = timestampMs),
        estimator = values,
        landmarks = landmarks,
        sourceImageWidth = IMAGE_SIZE,
        sourceImageHeight = IMAGE_SIZE,
    )

    private fun neutralValues(): Map<BlendShape, Float> = mapOf(
        BlendShape.CHEEK_PUFF to 0f,
        BlendShape.MOUTH_ROLL_LOWER to 0f,
        BlendShape.MOUTH_ROLL_UPPER to 0f,
        BlendShape.MOUTH_LEFT to 0f,
        BlendShape.MOUTH_RIGHT to 0f,
        BlendShape.MOUTH_FUNNEL to 0f,
        BlendShape.JAW_OPEN to 0f,
        BlendShape.MOUTH_SMILE_LEFT to 0f,
        BlendShape.MOUTH_SMILE_RIGHT to 0f,
    )

    private fun face(
        jawOutset: Float = 0f,
        mouthHalfWidth: Float = 20f,
    ): List<FaceLandmark> {
        val points = MutableList(LANDMARK_COUNT) { index ->
            landmark(200f + (index % 17) * 0.01f, 100f + (index % 19) * 0.01f, (index % 7) * 0.01f)
        }
        fun set(index: Int, x: Float, y: Float, z: Float = 0f) { points[index] = landmark(x, y, z) }
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
            val fraction = position.toFloat() / (FACE_OVAL.size - 1).toFloat()
            val side = if (position < FACE_OVAL.size / 2) 1f else -1f
            val outset = 60f + 20f * sin(fraction * kotlin.math.PI).toFloat()
            val delta = if (fraction >= 0.70f && fraction < 0.85f) jawOutset else 0f
            set(index, 200f + side * (outset + delta), 50f + 200f * fraction)
        }
        return points
    }

    private fun landmark(x: Float, y: Float, z: Float): FaceLandmark = FaceLandmark(
        x = x / IMAGE_SIZE,
        y = y / IMAGE_SIZE,
        relativeDepth = z / IMAGE_SIZE,
    )

    private companion object {
        const val IMAGE_SIZE: Int = 400
        const val LANDMARK_COUNT: Int = 478
        val FACE_OVAL: IntArray = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365,
            379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93,
            234, 127, 162, 21, 54, 103, 67, 109,
        )
    }
}
