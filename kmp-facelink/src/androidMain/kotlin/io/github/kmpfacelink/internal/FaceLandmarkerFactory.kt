package io.github.kmpfacelink.internal

import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.model.FaceTrackerConfig

private const val TAG = "FaceLandmarkerFactory"

/**
 * Creates [FaceLandmarker] instances from a [FaceTrackerConfig].
 *
 * Tries GPU delegate first; falls back to CPU if the GPU delegate is unavailable.
 */
internal object FaceLandmarkerFactory {

    /**
     * Creates a [FaceLandmarker] configured for live-stream mode.
     *
     * Attempts GPU delegate first. Falls back to CPU on [RuntimeException] (e.g. missing
     * OpenGL ES 3.1+ support or GPU driver rejection).
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
        return try {
            createWithDelegate(platformContext, config, resultListener, Delegate.GPU)
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e)
            createWithDelegate(platformContext, config, resultListener, Delegate.CPU)
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
                Log.e(TAG, "FaceLandmarker error: ${error.message}", error)
            }
            .build()

        return FaceLandmarker.createFromOptions(platformContext.context, options)
    }
}
