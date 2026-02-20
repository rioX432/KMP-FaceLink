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
import java.util.concurrent.Executors
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
    private val frameHandlers = mutableListOf<SharedFrameHandler>()
    private var cameraProvider: ProcessCameraProvider? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null
    private val imageConverter = ImageProxyConverter()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var isFrontCamera = true

    fun addFrameHandler(handler: SharedFrameHandler) {
        frameHandlers.add(handler)
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

    private fun processFrame(imageProxy: ImageProxy) {
        val bitmap = imageConverter.convert(imageProxy, mirrorHorizontally = isFrontCamera)
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        val width = bitmap.width
        val height = bitmap.height
        val timestampMs = SystemClock.elapsedRealtime()

        for (handler in frameHandlers) {
            handler.onFrame(bitmap, width, height, timestampMs)
        }

        imageConverter.returnBitmap(bitmap)
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
        frameHandlers.clear()
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
