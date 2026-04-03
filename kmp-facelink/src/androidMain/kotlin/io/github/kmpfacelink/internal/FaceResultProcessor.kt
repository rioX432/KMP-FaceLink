package io.github.kmpfacelink.internal

import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedFace
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
internal class FaceResultProcessor(
    private val config: FaceTrackerConfig,
    private val pipelineLock: PlatformLock,
    private val isReleased: () -> Boolean,
    private val isEnhancerDisabled: () -> Boolean,
) {

    private var smoother: BlendShapeSmoother? = config.createSmoother()
    private val enhancer: BlendShapeEnhancer? = BlendShapeEnhancer.create(config.enhancerConfig)
    private val calibrator = Calibrator(
        warmupFrames = config.calibrationConfig.warmupFrames,
        decayRate = config.calibrationConfig.rangeDecayRate,
    )

    /** Runs enhance → calibrate → smooth on the primary face. Returns null if tracker is released. */
    fun processPrimary(
        blendShapes: BlendShapeData,
        landmarks: List<FaceLandmark>,
        timestampMs: Long,
    ): BlendShapeData? {
        var result = blendShapes
        return pipelineLock.withLock {
            if (isReleased()) return null

            if (!isEnhancerDisabled()) {
                enhancer?.let { result = it.enhance(result, landmarks) }
            }

            if (config.calibrationConfig.enabled) {
                result = calibrator.calibrate(result)
            }

            smoother?.let { result = it.smooth(result, timestampMs) }

            // Defensive copy: pipeline stages return reusable scratch maps.
            // The result is stored in FaceTrackingData (held by the SharedFlow replay buffer),
            // so we must snapshot it before the next frame overwrites the scratch map.
            HashMap(result).filterByActive(config.activeBlendShapes)
        }
    }

    /** Builds [TrackedFace] list for all detected faces. Primary face uses pre-processed data. */
    fun buildTrackedFaces(
        result: FaceLandmarkerResult,
        processedPrimary: BlendShapeData,
        primaryLandmarks: List<FaceLandmark>,
        primaryHeadTransform: HeadTransform,
        timestampMs: Long,
    ): List<TrackedFace> {
        val faceCount = result.faceBlendshapes().get().size
        return List(faceCount) { index ->
            if (index == 0) {
                buildPrimaryFace(processedPrimary, primaryLandmarks, primaryHeadTransform)
            } else {
                buildSecondaryFace(result, index, timestampMs)
            }
        }
    }

    /** Must be called with [pipelineLock] already held. */
    fun resetCalibration() {
        calibrator.reset()
        smoother?.reset()
    }

    /** Must be called with [pipelineLock] already held. */
    fun updateSmoothing(smoothingConfig: SmoothingConfig) {
        smoother = smoothingConfig.createFaceSmoother()
    }

    private fun buildPrimaryFace(
        processedPrimary: BlendShapeData,
        primaryLandmarks: List<FaceLandmark>,
        primaryHeadTransform: HeadTransform,
    ): TrackedFace = TrackedFace(
        faceIndex = 0,
        blendShapes = processedPrimary,
        headTransform = primaryHeadTransform,
        gazeData = GazeEstimator.estimate(processedPrimary),
        landmarks = primaryLandmarks,
    )

    private fun buildSecondaryFace(
        result: FaceLandmarkerResult,
        index: Int,
        timestampMs: Long,
    ): TrackedFace {
        val landmarks = FaceLandmarkerResultMapper.extractLandmarks(result, index)
        val headTransform = FaceLandmarkerResultMapper.extractHeadTransform(result, index)
        // Secondary faces: apply only smoothing (no per-face calibration/enhancement state)
        var blendShapes = FaceLandmarkerResultMapper.extractBlendShapes(result, index)
        pipelineLock.withLock {
            smoother?.let {
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
        )
    }
}
