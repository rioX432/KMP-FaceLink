package io.github.kmpfacelink.internal

import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.model.CameraFacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shared CameraX setup logic used by both face and hand trackers.
 * Eliminates duplicated camera provider retrieval and resolution selector construction.
 */
internal class CameraXManager(
    private val platformContext: PlatformContext,
) {
    var cameraProvider: ProcessCameraProvider? = null
        private set

    suspend fun startCamera(
        cameraFacing: CameraFacing,
        imageAnalysis: ImageAnalysis,
        surfaceProvider: Preview.SurfaceProvider?,
    ) {
        val provider = getCameraProvider()
        cameraProvider = provider

        // CameraX bindToLifecycle/unbindAll require the main thread
        withContext(Dispatchers.Main.immediate) {
            bindCamera(provider, cameraFacing, imageAnalysis, surfaceProvider)
        }
    }

    private fun bindCamera(
        provider: ProcessCameraProvider,
        cameraFacing: CameraFacing,
        imageAnalysis: ImageAnalysis,
        surfaceProvider: Preview.SurfaceProvider?,
    ) {
        val cameraSelector = when (cameraFacing) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        provider.unbindAll()

        if (surfaceProvider != null) {
            val preview = Preview.Builder()
                .setResolutionSelector(previewResolutionSelector)
                .build().also { it.surfaceProvider = surfaceProvider }
            provider.bindToLifecycle(
                platformContext.lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
            )
        } else {
            provider.bindToLifecycle(
                platformContext.lifecycleOwner,
                cameraSelector,
                imageAnalysis,
            )
        }
    }

    /**
     * Unbinds all CameraX use cases, dispatching to the main thread if needed.
     * Safe to call from any thread (including non-suspend release paths).
     */
    fun unbindAll() {
        val provider = cameraProvider ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            provider.unbindAll()
        } else {
            mainHandler.post { provider.unbindAll() }
        }
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

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())

        private val previewResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        fun buildAnalysisResolutionSelector(width: Int, height: Int): ResolutionSelector =
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(width, height),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()

        fun buildPreviewResolutionSelector(): ResolutionSelector = previewResolutionSelector
    }
}
