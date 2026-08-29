package io.github.kmpfacelink.internal

import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.util.FaceLinkLogger

private const val TAG = "FaceLandmarkerFactory"

/**
 * Creates [FaceLandmarker] instances from a [FaceTrackerConfig].
 *
 * Uses [DelegateResolver] to apply the configured [FaceTrackerConfig.inferenceDelegate]
 * with automatic fallback when the preferred delegate is unavailable.
 */
internal object FaceLandmarkerFactory {

    /** Landmarker plus the delegate that actually survived the fallback chain. */
    internal data class Created(
        val landmarker: FaceLandmarker,
        val resolvedDelegate: Delegate,
    )

    /**
     * Creates a [FaceLandmarker] in the mode named by `config.performanceConfig.runningMode`.
     *
     * Applies the delegate preference from [config] with automatic fallback.
     * See [DelegateResolver] for the fallback chain behavior.
     *
     * @param platformContext Android platform context for asset loading
     * @param config Tracker configuration
     * @param resultListener Callback invoked with each detection result. Only
     *   [InferenceRunningMode.LIVE_STREAM] uses it — the other modes return the result from the
     *   `detect` call, and MediaPipe rejects a listener when they are selected.
     */
    fun create(
        platformContext: PlatformContext,
        config: FaceTrackerConfig,
        resultListener: (FaceLandmarkerResult, com.google.mediapipe.framework.image.MPImage) -> Unit,
    ): Created {
        val resolved = DelegateResolver.createResolvedWithFallback(
            config.performanceConfig.inferenceDelegate,
        ) { delegate ->
            createWithDelegate(platformContext, config, resultListener, delegate)
        }
        return Created(resolved.value, resolved.delegate)
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

        val mode = config.performanceConfig.runningMode
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(mode.toMediaPipe())
            .setNumFaces(config.detectionConfig.maxFaces)
            .setMinFaceDetectionConfidence(config.detectionConfig.minDetectionConfidence)
            .setMinTrackingConfidence(config.detectionConfig.minTrackingConfidence)
            .setMinFacePresenceConfidence(config.detectionConfig.minPresenceConfidence)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .apply {
                // MediaPipe throws if a listener is attached to a synchronous mode.
                if (mode == InferenceRunningMode.LIVE_STREAM) setResultListener(resultListener)
            }
            .setErrorListener { error ->
                FaceLinkLogger.e(TAG, "FaceLandmarker error: ${error.message}", error)
            }
            .build()

        return FaceLandmarker.createFromOptions(platformContext.context, options)
    }

    private fun InferenceRunningMode.toMediaPipe(): RunningMode = when (this) {
        InferenceRunningMode.IMAGE -> RunningMode.IMAGE
        InferenceRunningMode.VIDEO -> RunningMode.VIDEO
        InferenceRunningMode.LIVE_STREAM -> RunningMode.LIVE_STREAM
    }
}
