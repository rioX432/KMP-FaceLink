package io.github.kmpfacelink.internal

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateObservation
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.DimensionValue
import io.github.kmpfacelink.model.EstimatorArtifact
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingBackend
import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.model.PlatformIdentity
import io.github.kmpfacelink.model.SessionInstanceId
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackingContextEpoch
import io.github.kmpfacelink.research.CheekSignalCandidateFactories
import io.github.kmpfacelink.research.HashBoundStaticAppearanceModelProvider
import io.github.kmpfacelink.research.StaticAppearanceCandidateFactories
import io.github.kmpfacelink.research.StaticAppearanceModelByteLoader
import io.github.kmpfacelink.research.StaticAppearanceModelParser
import io.github.kmpfacelink.research.StaticAppearanceScoreModel
import io.github.kmpfacelink.util.PlatformLock
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalFaceLinkApi::class)
class CheekSignalLiveParityTest {
    @Test
    fun c3bOpaqueFeaturePlaneMatchesLiveProcessorAndDirectSeam() {
        val bytes = "synthetic-c3b-model".encodeToByteArray()
        val provider = HashBoundStaticAppearanceModelProvider(
            expectedSha256 = SYNTHETIC_MODEL_SHA256,
            byteLoader = StaticAppearanceModelByteLoader { bytes },
            parser = StaticAppearanceModelParser {
                StaticAppearanceScoreModel { features -> features.first() }
            },
        )
        val factories = listOf(StaticAppearanceCandidateFactories.c3bStaticCheekPatch(provider))
        val processor = FaceResultProcessor(
            config = FaceTrackerConfig(smoothingConfig = SmoothingConfig.None),
            pipelineLock = PlatformLock(),
            isReleased = { false },
            isEnhancerDisabled = { false },
            candidateProducerFactories = factories,
        )
        val direct = CandidateObservationSession(factories)
        val features = List(384) { index -> if (index == 0) 0.625f else 0f }
        val planes = mapOf(StaticAppearanceCandidateFactories.FEATURE_PLANE_KEY to features)
        val live = assertNotNull(
            processor.processPrimary(emptyMap(), emptyList(), 50L, epoch(), planes),
        ).candidateObservation
        val replay = direct.observe(
            CandidateObservationInput(
                identity = CandidateInputIdentity(1u, 0u, 0, 50L),
                estimator = emptyMap(),
                landmarks = emptyList(),
                sourceImageWidth = WIDTH,
                sourceImageHeight = HEIGHT,
                featurePlanes = planes,
            ),
        )

        assertEquals(signature(replay.alternatives), signature(live.alternatives))
        assertEquals(0.625f, live.alternatives.single().value)
        assertEquals(emptyMap(), live.selected)
    }

    @Test
    fun liveProcessorAndDirectReplaySeamUseTheSameFrozenFactoriesDefaultOff() {
        val factories = CheekSignalCandidateFactories.c2LipOnly()
        val processor = FaceResultProcessor(
            config = FaceTrackerConfig(smoothingConfig = SmoothingConfig.None),
            pipelineLock = PlatformLock(),
            isReleased = { false },
            isEnhancerDisabled = { false },
            candidateProducerFactories = factories,
        )
        val replaySeam = CandidateObservationSession(factories)
        val timestamps = listOf(0L) + List(15) { index -> 250L + index * 50L } + 1300L

        timestamps.forEach { timestampMs ->
            val values = values(timestampMs == 1300L)
            val live = assertNotNull(
                processor.processPrimary(values, emptyList(), timestampMs, epoch()),
            ).candidateObservation
            val replay = replaySeam.observe(
                CandidateObservationInput(
                    identity = CandidateInputIdentity(1u, 0u, 0, timestampMs),
                    estimator = values,
                    landmarks = emptyList(),
                    sourceImageWidth = WIDTH,
                    sourceImageHeight = HEIGHT,
                ),
            )
            assertEquals(signature(replay.alternatives), signature(live.alternatives))
            assertEquals(replay.selected, live.selected)
            assertEquals(0f, live.selected.getValue(BlendShape.CHEEK_PUFF))
        }
    }

    private fun signature(observations: List<CandidateObservation>): List<List<Any?>> = observations.map {
        listOf(
            it.producerId,
            it.implementationRevision,
            it.semanticId,
            it.semanticIntent,
            it.proposedTarget,
            it.value,
            it.readiness,
            it.reason,
            it.identity.contextEpochId,
            it.identity.processingStateRevision,
            it.identity.faceIndex,
            it.identity.timestampMs,
        )
    }

    private fun values(active: Boolean): Map<BlendShape, Float> = mapOf(
        BlendShape.CHEEK_PUFF to 0f,
        BlendShape.MOUTH_ROLL_LOWER to if (active) 0.2f else 0f,
        BlendShape.MOUTH_ROLL_UPPER to if (active) 0.3f else 0f,
        BlendShape.MOUTH_LEFT to if (active) 0.4f else 0f,
        BlendShape.MOUTH_RIGHT to 0f,
        BlendShape.MOUTH_FUNNEL to if (active) 0.1f else 0f,
        BlendShape.JAW_OPEN to 0f,
        BlendShape.MOUTH_SMILE_LEFT to 0f,
        BlendShape.MOUTH_SMILE_RIGHT to 0f,
    )

    private fun epoch(): TrackingContextEpoch = TrackingContextEpoch(
        epochId = 1u,
        sessionInstanceId = SessionInstanceId("candidate-parity"),
        estimator = EstimatorArtifact(
            family = FaceTrackingBackend.MEDIAPIPE,
            libraryVersion = DimensionValue.Known("test"),
            modelSha256 = DimensionValue.Known("a".repeat(64)),
        ),
        platform = PlatformIdentity(
            platform = "android",
            osVersion = DimensionValue.Known("test"),
            deviceModel = DimensionValue.Known("test"),
            estimatorConfigurationId = DimensionValue.Known("candidate-parity"),
        ),
        runningMode = DimensionValue.Known(InferenceRunningMode.LIVE_STREAM),
        resolvedDelegate = DimensionValue.Known("CPU"),
        inputWidth = DimensionValue.Known(WIDTH),
        inputHeight = DimensionValue.Known(HEIGHT),
        mirroredBeforeInference = DimensionValue.Known(true),
        configuredTransforms = emptyList(),
        activeBlendShapes = BlendShape.entries.toSet(),
    )

    private companion object {
        const val WIDTH: Int = 240
        const val HEIGHT: Int = 320
        const val SYNTHETIC_MODEL_SHA256: String =
            "04151d830c9801c978475455fe1f6c6ca489a86f493509449d2a946548c469f2"
    }
}
