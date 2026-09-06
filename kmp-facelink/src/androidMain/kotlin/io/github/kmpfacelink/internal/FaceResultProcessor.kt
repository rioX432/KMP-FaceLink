package io.github.kmpfacelink.internal

import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.CalibrationPhase
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceObservationProvenance
import io.github.kmpfacelink.model.FacePipelineKind
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.InferenceMaterialization
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedFace
import io.github.kmpfacelink.model.TrackingContextEpoch
import io.github.kmpfacelink.model.filterByActive
import io.github.kmpfacelink.util.BlendShapeEnhancer
import io.github.kmpfacelink.util.BlendShapeSmoother
import io.github.kmpfacelink.util.Calibrator
import io.github.kmpfacelink.util.GazeEstimator
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.createFaceSmoother
import io.github.kmpfacelink.util.createSmoother
import io.github.kmpfacelink.util.withLock

/**
 * Processes raw MediaPipe face results through the enhance → calibrate → smooth pipeline
 * and assembles multi-face [TrackedFace] output.
 *
 * Thread safety: [processPrimary] and [buildTrackedFaces] acquire [pipelineLock] internally
 * (called from the MediaPipe callback thread). [resetCalibration] and [updateSmoothing] must
 * be called with [pipelineLock] already held by the caller.
 */
internal class FaceResultProcessor private constructor(
    private val config: FaceTrackerConfig,
    private val pipelineLock: PlatformLock,
    private val isReleased: () -> Boolean,
    private val isEnhancerDisabled: () -> Boolean,
    private val calibrationState: CalibrationPipelineState,
    private val processingStateRevision: ULong,
) {

    constructor(
        config: FaceTrackerConfig,
        pipelineLock: PlatformLock,
        isReleased: () -> Boolean,
        isEnhancerDisabled: () -> Boolean,
    ) : this(
        config = config,
        pipelineLock = pipelineLock,
        isReleased = isReleased,
        isEnhancerDisabled = isEnhancerDisabled,
        calibrationState = CalibrationPipelineState(config),
        processingStateRevision = 0u,
    )

    private val primarySmoother: BlendShapeSmoother? = config.createSmoother()
    private val secondarySmoothers = mutableMapOf<Int, BlendShapeSmoother>()
    private val enhancer: BlendShapeEnhancer? = BlendShapeEnhancer.create(config.enhancerConfig)

    internal data class ProcessedPrimary(
        val blendShapes: BlendShapeData,
        val provenance: FaceObservationProvenance,
    )

    /** Runs enhance → calibrate → smooth on the primary face. Returns null if tracker is released. */
    fun processPrimary(
        blendShapes: BlendShapeData,
        landmarks: List<FaceLandmark>,
        timestampMs: Long,
        epoch: TrackingContextEpoch,
    ): ProcessedPrimary? {
        var result = blendShapes
        return pipelineLock.withLock {
            if (isReleased()) return null

            val enhancerApplied = enhancer != null && !isEnhancerDisabled()
            if (enhancerApplied) {
                result = enhancer.enhance(result, landmarks)
            }

            if (config.calibrationConfig.enabled) {
                result = calibrationState.calibrator.calibrate(result)
                calibrationState.frameCount++
                calibrationState.revision++
            }

            primarySmoother?.let { result = it.smooth(result, timestampMs) }

            // Defensive copy: pipeline stages return reusable scratch maps.
            // The result is stored in FaceTrackingData (held by the SharedFlow replay buffer),
            // so we must snapshot it before the next frame overwrites the scratch map.
            val emitted = HashMap(result).filterByActive(config.activeBlendShapes)
            ProcessedPrimary(
                blendShapes = emitted,
                provenance = FaceObservationProvenance(
                    contextEpochId = epoch.epochId,
                    faceIndex = 0,
                    pipelineKind = FacePipelineKind.PRIMARY,
                    appliedTransforms = effectiveConfig().fullPrimaryAppliedTransforms(enhancerApplied),
                    calibrationPhase = calibrationPhase(),
                    calibrationStateRevision = calibrationState.revision,
                    processingStateRevision = processingStateRevision,
                    materialization = InferenceMaterialization.FULL_INFERENCE,
                    emittedBlendShapes = emitted.keys.toSet(),
                ),
            )
        }
    }

    /** Builds [TrackedFace] list for all detected faces. Primary face uses pre-processed data. */
    @Suppress("LongParameterList")
    fun buildTrackedFaces(
        result: FaceLandmarkerResult,
        processedPrimary: ProcessedPrimary,
        primaryLandmarks: List<FaceLandmark>,
        primaryHeadTransform: HeadTransform,
        timestampMs: Long,
        epoch: TrackingContextEpoch,
    ): List<TrackedFace> {
        val faceCount = result.faceBlendshapes().get().size
        return List(faceCount) { index ->
            if (index == 0) {
                buildPrimaryFace(processedPrimary, primaryLandmarks, primaryHeadTransform)
            } else {
                buildSecondaryFace(result, index, timestampMs, epoch)
            }
        }
    }

    /**
     * Creates a reset calibration/smoothing lane without mutating processors already captured by
     * in-flight submissions. Must be called with [pipelineLock] already held.
     */
    fun forkResetCalibration(): FaceResultProcessor = FaceResultProcessor(
        config = config,
        pipelineLock = pipelineLock,
        isReleased = isReleased,
        isEnhancerDisabled = isEnhancerDisabled,
        calibrationState = CalibrationPipelineState(
            config = config,
            initialRevision = calibrationState.revision + 1u,
        ),
        processingStateRevision = processingStateRevision + 1u,
    )

    /**
     * Creates a fresh smoothing lane while preserving calibration state. In-flight submissions
     * keep their previous smoother and revision. Must be called with [pipelineLock] held.
     */
    fun forkResetSmoothing(): FaceResultProcessor = forkWithSmoothing(config.smoothingConfig)

    /**
     * Creates a lane for a new smoothing configuration while preserving calibration state.
     * Must be called with [pipelineLock] already held.
     */
    fun forkWithSmoothing(smoothingConfig: SmoothingConfig): FaceResultProcessor = FaceResultProcessor(
        config = config.copy(smoothingConfig = smoothingConfig),
        pipelineLock = pipelineLock,
        isReleased = isReleased,
        isEnhancerDisabled = isEnhancerDisabled,
        calibrationState = calibrationState,
        processingStateRevision = processingStateRevision + 1u,
    )

    private fun buildPrimaryFace(
        processedPrimary: ProcessedPrimary,
        primaryLandmarks: List<FaceLandmark>,
        primaryHeadTransform: HeadTransform,
    ): TrackedFace = TrackedFace(
        faceIndex = 0,
        blendShapes = processedPrimary.blendShapes,
        headTransform = primaryHeadTransform,
        gazeData = GazeEstimator.estimate(processedPrimary.blendShapes),
        landmarks = primaryLandmarks,
        provenance = processedPrimary.provenance,
    )

    private fun buildSecondaryFace(
        result: FaceLandmarkerResult,
        index: Int,
        timestampMs: Long,
        epoch: TrackingContextEpoch,
    ): TrackedFace {
        val landmarks = FaceLandmarkerResultMapper.extractLandmarks(result, index)
        val headTransform = FaceLandmarkerResultMapper.extractHeadTransform(result, index)
        // Secondary faces: apply only smoothing (no per-face calibration/enhancement state)
        var blendShapes = FaceLandmarkerResultMapper.extractBlendShapes(result, index)
        pipelineLock.withLock {
            secondarySmoother(index)?.let {
                // Defensive copy: smoother returns a reusable scratch map.
                blendShapes = HashMap(it.smooth(blendShapes, timestampMs))
            }
        }
        blendShapes = blendShapes.filterByActive(config.activeBlendShapes)
        return TrackedFace(
            faceIndex = index,
            blendShapes = blendShapes,
            headTransform = headTransform,
            gazeData = GazeEstimator.estimate(blendShapes),
            landmarks = landmarks,
            provenance = FaceObservationProvenance(
                contextEpochId = epoch.epochId,
                faceIndex = index,
                pipelineKind = FacePipelineKind.SECONDARY,
                appliedTransforms = effectiveConfig().secondaryAppliedTransforms(),
                calibrationPhase = CalibrationPhase.DISABLED,
                calibrationStateRevision = 0u,
                processingStateRevision = processingStateRevision,
                materialization = InferenceMaterialization.FULL_INFERENCE,
                emittedBlendShapes = blendShapes.keys.toSet(),
            ),
        )
    }

    private fun calibrationPhase(): CalibrationPhase = when {
        !config.calibrationConfig.enabled -> CalibrationPhase.DISABLED
        calibrationState.frameCount <= config.calibrationConfig.warmupFrames.toULong() -> CalibrationPhase.WARMUP
        else -> CalibrationPhase.ACTIVE
    }

    private fun effectiveConfig(): FaceTrackerConfig = config

    private fun secondarySmoother(index: Int): BlendShapeSmoother? {
        if (config.smoothingConfig is SmoothingConfig.None) return null
        return secondarySmoothers.getOrPut(index) {
            checkNotNull(config.smoothingConfig.createFaceSmoother())
        }
    }

    /** Calibration intentionally survives smoothing-only lane changes. */
    private class CalibrationPipelineState(
        config: FaceTrackerConfig,
        initialRevision: ULong = 0u,
    ) {
        val calibrator = Calibrator(
            warmupFrames = config.calibrationConfig.warmupFrames,
            decayRate = config.calibrationConfig.rangeDecayRate,
        )
        var frameCount: ULong = 0u
        var revision: ULong = initialRevision
    }
}
