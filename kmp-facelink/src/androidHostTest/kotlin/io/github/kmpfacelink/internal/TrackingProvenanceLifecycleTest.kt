package io.github.kmpfacelink.internal

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CalibrationConfig
import io.github.kmpfacelink.model.CalibrationPhase
import io.github.kmpfacelink.model.DimensionValue
import io.github.kmpfacelink.model.EstimatorArtifact
import io.github.kmpfacelink.model.FaceObservationProvenance
import io.github.kmpfacelink.model.FacePipelineKind
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingBackend
import io.github.kmpfacelink.model.FrameSkipConfig
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.InferenceMaterialization
import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.model.PlatformIdentity
import io.github.kmpfacelink.model.SessionInstanceId
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.ThermalState
import io.github.kmpfacelink.model.TrackingContextEpoch
import io.github.kmpfacelink.util.PlatformLock
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalFaceLinkApi::class)
class TrackingProvenanceLifecycleTest {

    @Test
    fun delayedCallbackRetainsSubmissionEpochAndMissingContextStaysMissing() {
        val store = SubmittedFrameContextStore()
        val oldEpoch = epoch(1u, "session-a", width = 240)
        val newEpoch = epoch(2u, "session-a", width = 320)
        val oldProcessor = processor()
        val newProcessor = oldProcessor.forkResetSmoothing()

        store.record(100L, SubmittedFrameContext(oldEpoch, oldProcessor))
        store.record(200L, SubmittedFrameContext(newEpoch, newProcessor))

        val oldContext = assertNotNull(store.take(100L))
        val newContext = assertNotNull(store.take(200L))
        assertEquals(oldEpoch, oldContext.epoch)
        assertEquals(newEpoch, newContext.epoch)
        assertTrue(oldContext.resultProcessor === oldProcessor)
        assertTrue(newContext.resultProcessor === newProcessor)
        assertTrue(oldContext.resultProcessor !== newContext.resultProcessor)
        assertNull(store.take(300L))
        assertEquals(0, store.size())
    }

    @Test
    fun delayedCallbackUsesTheProcessorCapturedBeforeSmoothingRotation() {
        val lock = PlatformLock()
        val oldProcessor = FaceResultProcessor(
            config = FaceTrackerConfig(smoothingConfig = SmoothingConfig.Ema(alpha = 0.5f)),
            pipelineLock = lock,
            isReleased = { false },
            isEnhancerDisabled = { false },
        )
        val oldEpoch = epoch(1u, "session-a", width = 240)
        assertNotNull(
            oldProcessor.processPrimary(
                blendShapes = mapOf(BlendShape.CHEEK_PUFF to 0f),
                landmarks = emptyList(),
                timestampMs = 1L,
                epoch = oldEpoch,
            ),
        )

        val newProcessor = oldProcessor.forkWithSmoothing(SmoothingConfig.Ema(alpha = 1f))
        val newEpoch = epoch(2u, "session-a", width = 240)
        val store = SubmittedFrameContextStore()
        store.record(100L, SubmittedFrameContext(oldEpoch, oldProcessor))
        store.record(200L, SubmittedFrameContext(newEpoch, newProcessor))

        val oldContext = assertNotNull(store.take(100L))
        val delayedOld = assertNotNull(
            oldContext.resultProcessor.processPrimary(
                blendShapes = mapOf(BlendShape.CHEEK_PUFF to 1f),
                landmarks = emptyList(),
                timestampMs = 2L,
                epoch = oldContext.epoch,
            ),
        )
        val newContext = assertNotNull(store.take(200L))
        val current = assertNotNull(
            newContext.resultProcessor.processPrimary(
                blendShapes = mapOf(BlendShape.CHEEK_PUFF to 1f),
                landmarks = emptyList(),
                timestampMs = 2L,
                epoch = newContext.epoch,
            ),
        )

        assertEquals(0.5f, delayedOld.blendShapes.getValue(BlendShape.CHEEK_PUFF))
        assertEquals(1f, current.blendShapes.getValue(BlendShape.CHEEK_PUFF))
        assertEquals(oldEpoch.epochId, delayedOld.provenance.contextEpochId)
        assertEquals(newEpoch.epochId, current.provenance.contextEpochId)
        assertEquals(0u, delayedOld.provenance.processingStateRevision)
        assertEquals(1u, current.provenance.processingStateRevision)
        assertEquals(1f, delayedOld.candidateObservation.estimator[BlendShape.CHEEK_PUFF])
        assertEquals(1f, delayedOld.candidateObservation.selected[BlendShape.CHEEK_PUFF])
        assertEquals(oldEpoch.epochId, delayedOld.candidateObservation.identity.contextEpochId)
        assertEquals(0u, delayedOld.candidateObservation.identity.processingStateRevision)
        assertEquals(0, delayedOld.candidateObservation.identity.faceIndex)
        assertEquals(2L, delayedOld.candidateObservation.identity.timestampMs)
        assertTrue(delayedOld.provenance.appliedTransforms != current.provenance.appliedTransforms)
    }

    @Test
    fun calibrationResetForksStateAndRestartsWarmupWithoutMutatingInflightLane() {
        val lock = PlatformLock()
        val config = FaceTrackerConfig(
            calibrationConfig = CalibrationConfig(
                enabled = true,
                warmupFrames = 2,
                rangeDecayRate = 1f,
            ),
        )
        val oldProcessor = FaceResultProcessor(
            config = config,
            pipelineLock = lock,
            isReleased = { false },
            isEnhancerDisabled = { false },
        )
        val oldEpoch = epoch(1u, "session-a", width = 240)
        listOf(0f, 1f, 0.5f).forEachIndexed { index, value ->
            oldProcessor.processPrimary(
                blendShapes = mapOf(BlendShape.CHEEK_PUFF to value),
                landmarks = emptyList(),
                timestampMs = index.toLong(),
                epoch = oldEpoch,
            )
        }

        val resetProcessor = oldProcessor.forkResetCalibration()
        val newEpoch = epoch(2u, "session-a", width = 240)
        val resetFirst = assertNotNull(
            resetProcessor.processPrimary(
                blendShapes = mapOf(BlendShape.CHEEK_PUFF to 0.7f),
                landmarks = emptyList(),
                timestampMs = 4L,
                epoch = newEpoch,
            ),
        )
        val delayedOld = assertNotNull(
            oldProcessor.processPrimary(
                blendShapes = mapOf(BlendShape.CHEEK_PUFF to 0.25f),
                landmarks = emptyList(),
                timestampMs = 4L,
                epoch = oldEpoch,
            ),
        )

        assertEquals(0.7f, resetFirst.blendShapes.getValue(BlendShape.CHEEK_PUFF))
        assertEquals(CalibrationPhase.WARMUP, resetFirst.provenance.calibrationPhase)
        assertEquals(1u, resetFirst.provenance.processingStateRevision)
        assertEquals(newEpoch.epochId, resetFirst.provenance.contextEpochId)
        assertEquals(CalibrationPhase.ACTIVE, delayedOld.provenance.calibrationPhase)
        assertEquals(0u, delayedOld.provenance.processingStateRevision)
        assertEquals(oldEpoch.epochId, delayedOld.provenance.contextEpochId)
        assertTrue(
            resetFirst.provenance.calibrationStateRevision > delayedOld.provenance.calibrationStateRevision,
        )
    }

    @Test
    fun interpolationCarriesOneEpochAndIsSealedOnRotation() {
        val config = FaceTrackerConfig(frameSkipConfig = FrameSkipConfig(enabled = true))
        val handler = FrameInterpolationHandler(config)
        val oldEpoch = epoch(1u, "session-a", width = 240)
        val provenance = FaceObservationProvenance(
            contextEpochId = oldEpoch.epochId,
            faceIndex = 0,
            pipelineKind = FacePipelineKind.PRIMARY,
            appliedTransforms = emptyList(),
            calibrationPhase = CalibrationPhase.DISABLED,
            calibrationStateRevision = 0u,
            processingStateRevision = 0u,
            materialization = InferenceMaterialization.FULL_INFERENCE,
            emittedBlendShapes = setOf(BlendShape.CHEEK_PUFF),
        )
        handler.onFrameProcessed(
            blendShapes = mapOf(BlendShape.CHEEK_PUFF to 0.25f),
            headTransform = HeadTransform(yaw = 1f),
            timestampMs = 100L,
            confidence = 0.9f,
            epoch = oldEpoch,
            provenance = provenance,
        )

        val interpolated = assertNotNull(
            handler.buildInterpolatedData(110L, 240, 320, ThermalState.NOMINAL),
        )
        assertEquals(oldEpoch, interpolated.contextEpoch)
        assertEquals(InferenceMaterialization.INTERPOLATED, interpolated.faces.single().provenance?.materialization)
        assertEquals(oldEpoch.epochId, interpolated.faces.single().provenance?.contextEpochId)

        val newEpoch = epoch(2u, "session-a", width = 320)
        handler.rotateTo(newEpoch)
        assertNull(handler.buildInterpolatedData(120L, 320, 240, ThermalState.NOMINAL))
    }

    private fun epoch(
        id: ULong,
        session: String,
        width: Int,
    ): TrackingContextEpoch = TrackingContextEpoch(
        epochId = id,
        sessionInstanceId = SessionInstanceId(session),
        estimator = EstimatorArtifact(
            family = FaceTrackingBackend.MEDIAPIPE,
            libraryVersion = DimensionValue.Known("test"),
            modelSha256 = DimensionValue.Known("a".repeat(64)),
        ),
        platform = PlatformIdentity(
            platform = "android",
            osVersion = DimensionValue.Known("test"),
            deviceModel = DimensionValue.Known("test"),
            estimatorConfigurationId = DimensionValue.Known("test"),
        ),
        runningMode = DimensionValue.Known(InferenceRunningMode.LIVE_STREAM),
        resolvedDelegate = DimensionValue.Known("CPU"),
        inputWidth = DimensionValue.Known(width),
        inputHeight = DimensionValue.Known(320),
        mirroredBeforeInference = DimensionValue.Known(true),
        configuredTransforms = emptyList(),
        activeBlendShapes = setOf(BlendShape.CHEEK_PUFF),
    )

    private fun processor(): FaceResultProcessor = FaceResultProcessor(
        config = FaceTrackerConfig(),
        pipelineLock = PlatformLock(),
        isReleased = { false },
        isEnhancerDisabled = { false },
    )
}
