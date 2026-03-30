package io.github.kmpfacelink.internal

import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.util.FaceLinkLogger

private const val TAG = "FaceLandmarkerFactory"

/**
 * Creates [FaceLandmarker] instances from a [FaceTrackerConfig].
 *
 * Uses [DelegateResolver] to apply the configured [FaceTrackerConfig.inferenceDelegate]
 * with automatic fallback when the preferred delegate is unavailable.
 */
internal object FaceLandmarkerFactory {

    /**
     * Creates a [FaceLandmarker] configured for live-stream mode.
     *
     * Applies the delegate preference from [config] with automatic fallback.
     * See [DelegateResolver] for the fallback chain behavior.
     *
     * @param platformContext Android platform context for asset loading
     * @param config Tracker configuration
     * @param resultListener Callback invoked with each detection result
     */
    fun create(
        platformContext: PlatformContext,
        config: FaceTrackerConfig,
        resultListener: (FaceLandmarkerResult, com.google.mediapipe.framework.image.MPImage) -> Unit,
    ): FaceLandmarker {
        return DelegateResolver.createWithFallback(config.inferenceDelegate) { delegate ->
            createWithDelegate(platformContext, config, resultListener, delegate)
        }
    }

    private fun createWithDelegate(
        platformContext: PlatformContext,
        config: FaceTrackerConfig,
        resultListener: (FaceLandmarkerResult, com.google.mediapipe.framework.image.MPImage) -> Unit,
        delegate: Delegate,
    ): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(config.modelAssetPath)
            .setDelegate(delegate)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(config.maxFaces)
            .setMinFaceDetectionConfidence(config.minDetectionConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setMinFacePresenceConfidence(config.minPresenceConfidence)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener(resultListener)
            .setErrorListener { error ->
                FaceLinkLogger.e(TAG, "FaceLandmarker error: ${error.message}", error)
            }
            .build()

        return FaceLandmarker.createFromOptions(platformContext.context, options)
    }
}
