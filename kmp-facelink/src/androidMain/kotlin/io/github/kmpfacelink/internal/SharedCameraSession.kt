package io.github.kmpfacelink.internal

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.model.CameraFacing
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Callback interface for receiving shared camera frames.
 * Implementations must NOT close the bitmap — the session manages its lifecycle.
 */
internal fun interface SharedFrameHandler {
    fun onFrame(bitmap: Bitmap, width: Int, height: Int, timestampMs: Long)
}

/**
 * Shared CameraX session that uses a single [ImageAnalysis] and distributes
 * converted bitmap frames to all registered [SharedFrameHandler]s.
 * This avoids CameraX surface count limits (typically Preview + 2 max).
 */
internal class SharedCameraSession(
    private val platformContext: PlatformContext,
) {
    private val handlersLock = Any()
    private val frameHandlers = mutableListOf<SharedFrameHandler>()
    private var cameraProvider: ProcessCameraProvider? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null
    private val imageConverter = ImageProxyConverter()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var isFrontCamera = true

    /**
     * Tracks bitmaps that are currently being processed by MediaPipe handlers.
     * Each handler's result callback must call [returnInFlightBitmap] to signal completion.
     */
    private val inFlightBitmaps = ConcurrentHashMap<Long, InFlightBitmap>()

    private class InFlightBitmap(
        val bitmap: Bitmap,
        val remaining: AtomicInteger,
    )

    fun addFrameHandler(handler: SharedFrameHandler) {
        synchronized(handlersLock) {
            frameHandlers.add(handler)
        }
    }

    fun setSurfaceProvider(provider: Preview.SurfaceProvider?) {
        surfaceProvider = provider
    }

    suspend fun start(cameraFacing: CameraFacing) {
        isFrontCamera = cameraFacing == CameraFacing.FRONT
        val provider = getCameraProvider()
        cameraProvider = provider

        val cameraSelector = when (cameraFacing) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        provider.unbindAll()

        val preview = surfaceProvider?.let { sp ->
            Preview.Builder()
                .setResolutionSelector(CameraXManager.buildPreviewResolutionSelector())
                .build().also { it.surfaceProvider = sp }
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(CameraXManager.buildAnalysisResolutionSelector())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, ::processFrame) }

        bindUseCases(provider, cameraSelector, preview, imageAnalysis)
    }

    /**
     * Called by each handler's MediaPipe result callback to signal that it is done
     * with the shared bitmap for the given timestamp.
     * When all handlers have signaled, the bitmap is returned to the pool.
     */
    fun returnInFlightBitmap(timestampMs: Long) {
        val entry = inFlightBitmaps[timestampMs] ?: return
        if (entry.remaining.decrementAndGet() <= 0) {
            inFlightBitmaps.remove(timestampMs)
            imageConverter.returnBitmap(entry.bitmap)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val bitmap = imageConverter.convert(imageProxy, mirrorHorizontally = isFrontCamera)
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        val width = bitmap.width
        val height = bitmap.height
        val timestampMs = SystemClock.elapsedRealtime()

        val handlers = synchronized(handlersLock) { frameHandlers.toList() }
        if (handlers.isEmpty()) {
            imageConverter.returnBitmap(bitmap)
            imageProxy.close()
            return
        }

        // Track the bitmap with ref count — handlers signal completion via returnInFlightBitmap
        inFlightBitmaps[timestampMs] = InFlightBitmap(bitmap, AtomicInteger(handlers.size))

        for (handler in handlers) {
            handler.onFrame(bitmap, width, height, timestampMs)
        }

        // Do NOT return bitmap here — handlers will return it via result callbacks
        imageProxy.close()
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        cameraSelector: CameraSelector,
        preview: Preview?,
        imageAnalysis: ImageAnalysis,
    ) {
        val groupBuilder = UseCaseGroup.Builder()
        if (preview != null) {
            groupBuilder.addUseCase(preview)
        }
        groupBuilder.addUseCase(imageAnalysis)
        provider.bindToLifecycle(
            platformContext.lifecycleOwner,
            cameraSelector,
            groupBuilder.build(),
        )
    }

    fun stop() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        stop()
        synchronized(handlersLock) { frameHandlers.clear() }
        inFlightBitmaps.clear()
        imageConverter.release()
        analysisExecutor.shutdown()
    }

    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(platformContext.context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(platformContext.context),
            )
        }
}
