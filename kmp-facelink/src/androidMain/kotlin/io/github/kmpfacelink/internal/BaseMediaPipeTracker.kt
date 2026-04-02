package io.github.kmpfacelink.internal

import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.ThermalMonitor
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Bundles the construction parameters shared by all MediaPipe-based trackers.
 */
internal class MediaPipeTrackerParams(
    val platformContext: PlatformContext,
    val sharedCameraSession: SharedCameraSession?,
    val analysisWidth: Int,
    val analysisHeight: Int,
    val initialCameraFacing: CameraFacing,
)

/**
 * Base class for MediaPipe-based Android trackers that share camera pipeline
 * infrastructure, executor management, and lifecycle patterns.
 *
 * Subclasses must implement:
 * - [initLandmarker] to create the MediaPipe landmarker
 * - [closeLandmarker] to close the landmarker
 * - [buildDetectionCallbacks] to supply tracker-specific detection logic
 * - [onSharedFrameReceived] / [onImageProxyReceived] for frame processing
 * - [performExtraRelease] for tracker-specific cleanup
 * - [tag] for logging
 *
 * @param T The tracking data type
 */
@OptIn(ExperimentalAtomicApi::class)
internal abstract class BaseMediaPipeTracker<T>(
    private val params: MediaPipeTrackerParams,
    thermalMonitor: ThermalMonitor,
    frameThrottler: FrameThrottler,
) : BaseTracker<T>(
    thermalMonitor = thermalMonitor,
    frameThrottler = frameThrottler,
) {

    protected val platformContext: PlatformContext = params.platformContext

    override val trackingData: Flow<T> = super.trackingData
    override val state: StateFlow<TrackingState> = super.state
    override val errorMessage: StateFlow<String?> = super.errorMessage

    @Volatile
    internal var currentCameraFacing: CameraFacing = params.initialCameraFacing
    protected val isFrontCamera: Boolean
        get() = currentCameraFacing == CameraFacing.FRONT

    protected val analysisExecutor = Executors.newSingleThreadExecutor()
    protected val imageConverter = ImageProxyConverter()
    protected val cameraManager = if (params.sharedCameraSession == null) CameraXManager(platformContext) else null

    // Lazy to avoid accessing abstract `tag` during superclass init
    protected val cameraPipeline: CameraPipelineHelper by lazy {
        CameraPipelineHelper(
            tag = tag,
            sharedCameraSession = params.sharedCameraSession,
            cameraManager = cameraManager,
            imageConverter = imageConverter,
            analysisExecutor = analysisExecutor,
        )
    }

    private var previewSurfaceProvider: Preview.SurfaceProvider? = null

    /** Logging tag for this tracker. */
    protected abstract val tag: String

    /** Initialize the MediaPipe landmarker. Called during [start]. */
    protected abstract fun initLandmarker()

    /** Close the MediaPipe landmarker and nullify the reference. */
    protected abstract fun closeLandmarker()

    /** Build detection callbacks for the current landmarker state. */
    protected abstract fun buildDetectionCallbacks(): DetectionCallbacks

    /** Handle a shared frame from [SharedCameraSession]. */
    protected abstract fun onSharedFrameReceived(
        bitmap: android.graphics.Bitmap,
        width: Int,
        height: Int,
        timestampMs: Long,
    )

    /** Handle an [ImageProxy] frame from the independent camera. */
    protected abstract fun onImageProxyReceived(imageProxy: ImageProxy)

    /** Perform tracker-specific cleanup during [release]. Called inside pipelineLock. */
    protected open fun performExtraRelease() {}

    internal fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?) {
        previewSurfaceProvider = surfaceProvider
    }

    internal suspend fun performStart() {
        pipelineLock.withLock {
            if (!guardStart()) return
        }

        try {
            initLandmarker()
            startCamera()
            pipelineLock.withLock { transitionToTracking() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            FaceLinkLogger.e(tag, "Failed to start tracking", e)
            setError(e.message ?: e.toString())
            throw e
        }
    }

    internal suspend fun performStop() {
        pipelineLock.withLock {
            cameraManager?.unbindAll()
            closeLandmarker()
            mutableState.value = TrackingState.STOPPED
        }
    }

    internal fun performRelease() {
        pipelineLock.withLock {
            released.store(1)
            cameraManager?.unbindAll()
            closeLandmarker()
            imageConverter.release()
            thermalMonitor.release()
            performExtraRelease()
            analysisExecutor.shutdown()
            mutableState.value = TrackingState.RELEASED
        }
    }

    internal suspend fun performSwitchCamera(facing: CameraFacing) {
        pipelineLock.withLock { checkNotReleased("switch camera") }
        if (currentCameraFacing == facing) return
        val wasTracking = mutableState.value == TrackingState.TRACKING
        if (wasTracking) performStop()
        currentCameraFacing = facing
        if (wasTracking) performStart()
    }

    private suspend fun startCamera() {
        cameraPipeline.startCamera(
            config = CameraStartConfig(
                cameraFacing = currentCameraFacing,
                analysisWidth = params.analysisWidth,
                analysisHeight = params.analysisHeight,
                surfaceProvider = previewSurfaceProvider,
            ),
            onSharedFrame = ::onSharedFrameReceived,
            onImageProxy = ::onImageProxyReceived,
        )
    }

    /**
     * Helper for subclasses to process a shared frame using [cameraPipeline].
     */
    protected fun delegateSharedFrame(
        bitmap: android.graphics.Bitmap,
        width: Int,
        height: Int,
        timestampMs: Long,
    ) {
        cameraPipeline.processSharedFrame(
            bitmap = bitmap,
            width = width,
            height = height,
            timestampMs = timestampMs,
            context = SharedFrameContext(mutableState.value, buildDetectionCallbacks()),
        )
    }

    /**
     * Helper for subclasses to process an [ImageProxy] using [cameraPipeline].
     */
    protected fun delegateImageProxy(imageProxy: ImageProxy) {
        cameraPipeline.processFrame(
            imageProxy = imageProxy,
            trackingState = mutableState.value,
            isFrontCamera = isFrontCamera,
            callbacks = buildDetectionCallbacks(),
        )
    }
}
