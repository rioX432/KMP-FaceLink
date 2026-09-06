@file:Suppress("MagicNumber")

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.DimensionValue
import io.github.kmpfacelink.model.EstimatorArtifact
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingBackend
import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.model.PlatformIdentity
import io.github.kmpfacelink.model.SessionInstanceId
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackingContextEpoch
import io.github.kmpfacelink.research.C0NativeCheekPuffControl
import io.github.kmpfacelink.research.C1FrozenContourCandidate
import io.github.kmpfacelink.research.C1StandaloneFrameRole
import io.github.kmpfacelink.util.PlatformLock
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalFaceLinkApi::class)
class C0C1LiveParityTest {
    @Test
    fun estimatorControlAndStandaloneContourMatchDirectAndAndroidLiveSeams() {
        val factories = listOf(C1FrozenContourCandidate.factory())
        val processor = FaceResultProcessor(
            config = FaceTrackerConfig(smoothingConfig = SmoothingConfig.None),
            pipelineLock = PlatformLock(),
            isReleased = { false },
            isEnhancerDisabled = { false },
            candidateProducerFactories = factories,
        )
        val direct = CandidateObservationSession(factories)
        val rows = buildList {
            add(Triple(0L, C1StandaloneFrameRole.BEFORE_CALIBRATION, face()))
            repeat(15) { index ->
                add(Triple(1_500L + index * 100L, C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION, face()))
            }
            add(Triple(4_100L, C1StandaloneFrameRole.EVALUATION, face(jawOutset = 4f)))
        }

        rows.forEach { (timestampMs, role, landmarks) ->
            val estimator = mapOf(BlendShape.CHEEK_PUFF to 0.044f)
            val planes = mapOf(C1FrozenContourCandidate.FRAME_ROLE_PLANE_KEY to listOf(role.encodedValue))
            val live = assertNotNull(
                processor.processPrimary(estimator, landmarks, timestampMs, epoch(), planes),
            ).candidateObservation
            val replay = direct.observe(
                CandidateObservationInput(
                    CandidateInputIdentity(1u, 0u, 0, timestampMs),
                    estimator,
                    landmarks,
                    IMAGE_SIZE,
                    IMAGE_SIZE,
                    planes,
                ),
            )

            assertEquals(
                replay.alternatives.map { listOf(it.value, it.readiness, it.reason) },
                live.alternatives.map { listOf(it.value, it.readiness, it.reason) },
            )
            assertEquals(replay.selected, live.selected)
            assertEquals(
                C0NativeCheekPuffControl.read(replay).value,
                C0NativeCheekPuffControl.read(live).value,
            )
        }
    }

    private fun epoch(): TrackingContextEpoch = TrackingContextEpoch(
        epochId = 1u,
        sessionInstanceId = SessionInstanceId("c0-c1-live-parity"),
        estimator = EstimatorArtifact(
            family = FaceTrackingBackend.MEDIAPIPE,
            libraryVersion = DimensionValue.Known("test"),
            modelSha256 = DimensionValue.Known("a".repeat(64)),
        ),
        platform = PlatformIdentity(
            platform = "android",
            osVersion = DimensionValue.Known("test"),
            deviceModel = DimensionValue.Known("test"),
            estimatorConfigurationId = DimensionValue.Known("c0-c1-live-parity"),
        ),
        runningMode = DimensionValue.Known(InferenceRunningMode.LIVE_STREAM),
        resolvedDelegate = DimensionValue.Known("CPU"),
        inputWidth = DimensionValue.Known(IMAGE_SIZE),
        inputHeight = DimensionValue.Known(IMAGE_SIZE),
        mirroredBeforeInference = DimensionValue.Known(true),
        configuredTransforms = emptyList(),
        activeBlendShapes = BlendShape.entries.toSet(),
    )

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
