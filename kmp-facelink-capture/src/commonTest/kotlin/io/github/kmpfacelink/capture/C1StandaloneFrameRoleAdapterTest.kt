@file:Suppress("MagicNumber")

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.research.C0NativeCheekPuffControl
import io.github.kmpfacelink.research.C1FrozenContourCandidate
import io.github.kmpfacelink.research.C1StandaloneFrameRole
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalFaceLinkApi::class)
class C1StandaloneFrameRoleAdapterTest {
    @Test
    fun sequenceTrimExactlyMatchesHistoricalRoundRule() {
        val span = openingSpan()
        assertEquals(C1StandaloneFrameRole.BEFORE_CALIBRATION, C1StandaloneFrameRoleAdapter.role(span, 54))
        assertEquals(C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION, C1StandaloneFrameRoleAdapter.role(span, 55))
        assertEquals(C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION, C1StandaloneFrameRoleAdapter.role(span, 130))
        assertEquals(C1StandaloneFrameRole.EVALUATION, C1StandaloneFrameRoleAdapter.role(span, 131))
    }

    @Test
    fun invalidOpeningSpanFailsClosed() {
        assertFailsWith<IllegalArgumentException> {
            C1StandaloneFrameRoleAdapter.role(openingSpan().copy(cue = "RELEASE"), 55)
        }
        assertFailsWith<IllegalArgumentException> {
            C1StandaloneFrameRoleAdapter.role(openingSpan().copy(endMs = 2_000L), 55)
        }
    }

    @Test
    fun directAndRawRecordReplayUseIdenticalStandaloneRolesAndValues() {
        val factories = listOf(C1FrozenContourCandidate.factory())
        val direct = CandidateObservationSession(factories)
        val replay = CandidateObservationReplaySession(factories)
        val span = openingSpan()
        val names = listOf("_neutral") + BlendShape.entries
            .filterNot { it == BlendShape.TONGUE_OUT }
            .map { it.arKitName }
        val values = FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT)
        values[names.indexOf(BlendShape.CHEEK_PUFF.arKitName)] = 0.044f
        val sequences = (55..69).toList() + 131

        sequences.forEachIndexed { index, sequence ->
            val landmarks = if (sequence == 131) face(jawOutset = 4f) else face()
            val rolePlanes = C1StandaloneFrameRoleAdapter.featurePlanes(span, sequence)
            val timestampMs = 1_500L + index * 100L
            val identity = CandidateInputIdentity(3u, 0u, 0, timestampMs)
            val estimator = buildMap {
                names.forEachIndexed { valueIndex, name ->
                    BlendShape.fromArKitName(name)?.let { shape -> put(shape, values[valueIndex]) }
                }
            }
            val directReceipt = direct.observe(
                CandidateObservationInput(
                    identity,
                    estimator,
                    landmarks,
                    IMAGE_SIZE,
                    IMAGE_SIZE,
                    rolePlanes,
                ),
            )
            val replayReceipt = replay.observe(
                record = InferenceRecord(
                    analysisFrameSeq = sequence,
                    captureTimestampNs = timestampMs * 1_000_000L,
                    resultElapsedNs = timestampMs * 1_000_000L,
                    phase = if (sequence <= span.lastFrameSeq) CapturePhase.NEUTRAL else CapturePhase.ACTED,
                    hasFace = true,
                    inferenceFailed = false,
                    landmarks = landmarks.toRawArray(),
                    blendshapes = values,
                    transform = FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS),
                ),
                blendshapeNames = names,
                identity = identity,
                sourceImageWidth = IMAGE_SIZE,
                sourceImageHeight = IMAGE_SIZE,
                featurePlanes = rolePlanes,
            )

            assertEquals(
                directReceipt.alternatives.map { listOf(it.value, it.readiness, it.reason) },
                replayReceipt.alternatives.map { listOf(it.value, it.readiness, it.reason) },
            )
            assertEquals(directReceipt.selected, replayReceipt.selected)
            assertEquals(
                C0NativeCheekPuffControl.read(directReceipt).value,
                C0NativeCheekPuffControl.read(replayReceipt).value,
            )
        }
    }

    private fun openingSpan(): TakeSpan = TakeSpan(
        cue = "NEUTRAL",
        phaseCode = CapturePhase.NEUTRAL.code,
        startMs = 2_000L,
        endMs = 6_000L,
        firstFrameSeq = 10,
        lastFrameSeq = 130,
    )

    private fun List<FaceLandmark>.toRawArray(): FloatArray = FloatArray(size * 3).also { output ->
        forEachIndexed { index, point ->
            output[index * 3] = point.x
            output[index * 3 + 1] = point.y
            output[index * 3 + 2] = checkNotNull(point.relativeDepth)
        }
    }

    private fun face(jawOutset: Float = 0f): List<FaceLandmark> {
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
        set(61, 180f, 170f)
        set(291, 220f, 170f)
        set(13, 200f, 168f)
        set(14, 200f, 172f)
        FACE_OVAL.forEachIndexed { position, landmarkIndex ->
            if (landmarkIndex == 10 || landmarkIndex == 152) return@forEachIndexed
            val fraction = position.toFloat() / (FACE_OVAL.size - 1).toFloat()
            val side = if (position < FACE_OVAL.size / 2) 1f else -1f
            val outset = 60f + 20f * sin(fraction * kotlin.math.PI).toFloat()
            val delta = if (fraction >= 0.70f && fraction < 0.85f) jawOutset else 0f
            set(landmarkIndex, 200f + side * (outset + delta), 50f + 200f * fraction)
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
