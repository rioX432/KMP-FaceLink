@file:Suppress("MagicNumber")

package io.github.kmpfacelink.research

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.CandidateReadiness
import io.github.kmpfacelink.model.FaceLandmark
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalFaceLinkApi::class)
class C1FrozenContourCandidateTest {
    @Test
    fun standaloneRoleScheduleProducesBinaryAlternativeAndLeavesEstimatorSelected() {
        val session = calibratedSession()
        val fired = session.observe(input(4_100L, C1StandaloneFrameRole.EVALUATION, face(jawOutset = 4f)))
        val blocked = session.observe(
            input(4_200L, C1StandaloneFrameRole.EVALUATION, face(jawOutset = 4f, mouthHalfWidth = 23f)),
        )

        assertEquals(1f, fired.alternatives.single().value)
        assertEquals(0f, blocked.alternatives.single().value)
        assertEquals(BlendShape.CHEEK_PUFF, fired.alternatives.single().proposedTarget)
        assertEquals(0.02f, fired.selected.getValue(BlendShape.CHEEK_PUFF))
        assertEquals(CandidateReadiness.READY, blocked.alternatives.single().readiness)
    }

    @Test
    fun burnInAndCalibrationAreAbsentRatherThanNumericZero() {
        val session = CandidateObservationSession(listOf(C1FrozenContourCandidate.factory()))
        val burnIn = session.observe(input(0L, C1StandaloneFrameRole.BEFORE_CALIBRATION, face()))
        val calibration = session.observe(input(1_500L, C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION, face()))

        assertNull(burnIn.alternatives.single().value)
        assertEquals("c1_opening_neutral_burn_in", burnIn.alternatives.single().reason)
        assertNull(calibration.alternatives.single().value)
        assertEquals("c1_opening_neutral_calibration", calibration.alternatives.single().reason)
    }

    @Test
    fun missingRoleAndTooFewCalibrationFramesFailClosed() {
        val missingRole = CandidateObservationSession(listOf(C1FrozenContourCandidate.factory())).observe(
            CandidateObservationInput(
                CandidateInputIdentity(1u, 0u, 0, 0L),
                mapOf(BlendShape.CHEEK_PUFF to 0.02f),
                face(),
                IMAGE_SIZE,
                IMAGE_SIZE,
            ),
        ).alternatives.single()
        assertNull(missingRole.value)
        assertEquals(CandidateReadiness.MISSING_INPUT, missingRole.readiness)

        val session = CandidateObservationSession(listOf(C1FrozenContourCandidate.factory()))
        repeat(14) { index ->
            session.observe(input(1_500L + index * 50L, C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION, face()))
        }
        val insufficient = session.observe(input(4_100L, C1StandaloneFrameRole.EVALUATION, face()))
            .alternatives.single()
        assertNull(insufficient.value)
        assertEquals(CandidateReadiness.REJECTED, insufficient.readiness)
        assertEquals("c1_insufficient_opening_neutral_frames", insufficient.reason)
    }

    @Test
    fun roleRegressionAndDimensionChangeAreTerminalRejections() {
        val regressedSession = calibratedSession()
        regressedSession.observe(input(4_100L, C1StandaloneFrameRole.EVALUATION, face()))
        val regressed = regressedSession.observe(
            input(4_200L, C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION, face()),
        ).alternatives.single()
        assertEquals(CandidateReadiness.REJECTED, regressed.readiness)
        assertEquals("c1_frame_role_regressed", regressed.reason)

        val changedDimensions = CandidateObservationSession(listOf(C1FrozenContourCandidate.factory()))
        changedDimensions.observe(input(1_500L, C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION, face()))
        val changed = changedDimensions.observe(
            input(1_550L, C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION, face(), width = 401),
        ).alternatives.single()
        assertEquals(CandidateReadiness.REJECTED, changed.readiness)
        assertEquals("c1_source_dimensions_changed_within_epoch", changed.reason)
    }

    private fun calibratedSession(): CandidateObservationSession =
        CandidateObservationSession(listOf(C1FrozenContourCandidate.factory())).also { session ->
            session.observe(input(0L, C1StandaloneFrameRole.BEFORE_CALIBRATION, face()))
            repeat(15) { index ->
                session.observe(
                    input(1_500L + index * 100L, C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION, face()),
                )
            }
        }

    private fun input(
        timestampMs: Long,
        role: C1StandaloneFrameRole,
        landmarks: List<FaceLandmark>,
        width: Int = IMAGE_SIZE,
    ): CandidateObservationInput = CandidateObservationInput(
        identity = CandidateInputIdentity(1u, 0u, 0, timestampMs),
        estimator = mapOf(BlendShape.CHEEK_PUFF to 0.02f),
        landmarks = landmarks,
        sourceImageWidth = width,
        sourceImageHeight = IMAGE_SIZE,
        featurePlanes = mapOf(C1FrozenContourCandidate.FRAME_ROLE_PLANE_KEY to listOf(role.encodedValue)),
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
