package io.github.kmpfacelink.internal

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.util.FaceLinkLogger
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * Configuration for starting the camera pipeline.
 * Groups camera-facing, analysis resolution, and preview surface.
 */
internal class CameraStartConfig(
    val cameraFacing: CameraFacing,
    val analysisWidth: Int,
    val analysisHeight: Int,
    val surfaceProvider: Preview.SurfaceProvider?,
)

/**
 * Context for processing a shared frame, grouping tracking state and detection callbacks.
 */
internal class SharedFrameContext(
    val trackingState: TrackingState,
    val callbacks: DetectionCallbacks,
)

/**
 * Tracker-specific detection callbacks passed to [CameraPipelineHelper] frame methods.
 * Bundles landmarker readiness, throttling, and async dispatch into a single object
 * to avoid excessive parameter counts.
 */
internal class DetectionCallbacks(
    val isLandmarkerReady: Boolean,
    val shouldProcess: (Long) -> Boolean,
    val detectAsync: (MPImage, Long) -> Unit,
)

/**
 * Encapsulates the camera pipeline logic shared by all MediaPipe-based trackers.
 *
 * Handles camera setup (shared vs independent), frame throttling, ImageProxy conversion,
 * bitmap lifecycle management, and async detection dispatch. Each tracker delegates its
 * camera pipeline to an instance of this helper, supplying tracker-specific callbacks
 * via [DetectionCallbacks].
 */
internal class CameraPipelineHelper(
    private val tag: String,
    private val sharedCameraSession: SharedCameraSession?,
    private val cameraManager: CameraXManager?,
    private val imageConverter: ImageProxyConverter,
    private val analysisExecutor: Executor,
) {

    /** Currently in-flight bitmap for independent camera path. */
    internal val inFlightBitmap: AtomicReference<Bitmap?> = AtomicReference(null)

    @Volatile
    internal var lastImageWidth: Int = 0

    @Volatile
    internal var lastImageHeight: Int = 0

    /**
     * Starts the camera pipeline, either joining a shared session or binding an
     * independent CameraX analysis use-case.
     */
    internal suspend fun startCamera(
        config: CameraStartConfig,
        onSharedFrame: SharedFrameHandler,
        onImageProxy: (ImageProxy) -> Unit,
    ) {
        if (sharedCameraSession != null) {
            sharedCameraSession.addFrameHandler(onSharedFrame)
        } else {
            val imageAnalysis = buildImageAnalysis(
                config.analysisWidth,
                config.analysisHeight,
                onImageProxy,
            )
            cameraManager!!.startCamera(
                cameraFacing = config.cameraFacing,
                imageAnalysis = imageAnalysis,
                surfaceProvider = config.surfaceProvider,
            )
        }
    }

    /**
     * Processes an [ImageProxy] frame from the independent camera path.
     *
     * Checks landmarker readiness and tracking state, applies throttling, converts the
     * image, and dispatches async detection via [DetectionCallbacks.detectAsync].
     */
    internal fun processFrame(
        imageProxy: ImageProxy,
        trackingState: TrackingState,
        isFrontCamera: Boolean,
        callbacks: DetectionCallbacks,
    ) {
        if (!callbacks.isLandmarkerReady || trackingState != TrackingState.TRACKING) {
            imageProxy.close()
            return
        }

        if (!callbacks.shouldProcess(SystemClock.elapsedRealtime())) {
            imageProxy.close()
            return
        }

        val bitmap = imageConverter.convert(imageProxy, mirrorHorizontally = isFrontCamera)
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        lastImageWidth = bitmap.width
        lastImageHeight = bitmap.height

        dispatchDetection(bitmap, SystemClock.elapsedRealtime(), callbacks)
        imageProxy.close()
    }

    /**
     * Processes a shared bitmap frame from the [SharedCameraSession].
     *
     * Checks landmarker readiness and tracking state, applies throttling, builds an
     * [MPImage], and dispatches async detection via [DetectionCallbacks.detectAsync].
     */
    internal fun processSharedFrame(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        timestampMs: Long,
        context: SharedFrameContext,
    ) {
        if (!context.callbacks.isLandmarkerReady || context.trackingState != TrackingState.TRACKING) {
            sharedCameraSession?.returnInFlightBitmap(timestampMs)
            return
        }

        if (!context.callbacks.shouldProcess(timestampMs)) {
            sharedCameraSession?.returnInFlightBitmap(timestampMs)
            return
        }

        lastImageWidth = width
        lastImageHeight = height

        val mpImage = BitmapImageBuilder(bitmap).build()
        try {
            context.callbacks.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            FaceLinkLogger.w(tag, "detectAsync failed", e)
            sharedCameraSession?.returnInFlightBitmap(timestampMs)
        }
    }

    /**
     * Returns the in-flight bitmap after a MediaPipe result callback completes.
     * Handles both shared (ref-counted) and independent (AtomicReference) camera paths.
     */
    internal fun returnBitmapAfterResult(timestampMs: Long) {
        if (sharedCameraSession != null) {
            sharedCameraSession.returnInFlightBitmap(timestampMs)
        } else {
            inFlightBitmap.getAndSet(null)?.let { imageConverter.returnBitmap(it) }
        }
    }

    private fun buildImageAnalysis(
        analysisWidth: Int,
        analysisHeight: Int,
        onImageProxy: (ImageProxy) -> Unit,
    ): ImageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            CameraXManager.buildAnalysisResolutionSelector(analysisWidth, analysisHeight),
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also { it.setAnalyzer(analysisExecutor, onImageProxy) }

    /** Builds MPImage, tracks the bitmap, and calls detectAsync. */
    private fun dispatchDetection(
        bitmap: Bitmap,
        timestampMs: Long,
        callbacks: DetectionCallbacks,
    ) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        inFlightBitmap.set(bitmap)

        try {
            callbacks.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            FaceLinkLogger.w(tag, "detectAsync failed", e)
            if (inFlightBitmap.compareAndSet(bitmap, null)) {
                imageConverter.returnBitmap(bitmap)
            }
        }
    }
}
