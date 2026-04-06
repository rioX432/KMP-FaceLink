package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.ThermalState
import io.github.kmpfacelink.model.TrackedFace
import io.github.kmpfacelink.model.filterByActive
import io.github.kmpfacelink.util.BlendShapeInterpolator
import io.github.kmpfacelink.util.ConfidenceFrameStrategy
import io.github.kmpfacelink.util.FrameDecision
import io.github.kmpfacelink.util.GazeEstimator

/**
 * Handles confidence-guided frame skipping and interpolation for skipped frames.
 *
 * When tracking confidence is stable and blend shapes are not changing significantly,
 * frames can be skipped and replaced with interpolated data to reduce processing load.
 */
internal class FrameInterpolationHandler(config: FaceTrackerConfig) {

    private val frameSkipStrategy = ConfidenceFrameStrategy(config.frameSkipConfig)
    private val interpolator = BlendShapeInterpolator()
    private val activeBlendShapes = config.activeBlendShapes

    @Volatile
    var lastConfidence: Float = 0f
        private set

    /** Returns true when the current frame should be skipped based on confidence stability. */
    fun shouldSkip(): Boolean =
        frameSkipStrategy.evaluate(lastConfidence) == FrameDecision.SKIP

    /** Updates internal state after a frame is fully processed. */
    fun onFrameProcessed(
        blendShapes: BlendShapeData,
        headTransform: HeadTransform,
        timestampMs: Long,
        confidence: Float,
    ) {
        lastConfidence = confidence
        interpolator.onFrameProcessed(blendShapes, headTransform, timestampMs)
        frameSkipStrategy.onFrameProcessed(blendShapes)
    }

    /** Builds interpolated [FaceTrackingData] for a skipped frame, or null if not enough history. */
    fun buildInterpolatedData(
        timestampMs: Long,
        imageWidth: Int,
        imageHeight: Int,
        thermalState: ThermalState,
    ): FaceTrackingData? {
        val blendShapes = interpolator.interpolateBlendShapes(timestampMs) ?: return null
        val headTransform = interpolator.interpolateTransform(timestampMs) ?: return null
        val filtered = blendShapes.filterByActive(activeBlendShapes)
        val gaze = GazeEstimator.estimate(filtered)

        return FaceTrackingData(
            blendShapes = filtered,
            headTransform = headTransform,
            gazeData = gaze,
            sourceImageWidth = imageWidth,
            sourceImageHeight = imageHeight,
            timestampMs = timestampMs,
            isTracking = true,
            faces = listOf(
                TrackedFace(
                    faceIndex = 0,
                    blendShapes = filtered,
                    headTransform = headTransform,
                    gazeData = gaze,
                    confidence = lastConfidence,
                ),
            ),
            thermalState = thermalState,
        )
    }
}
