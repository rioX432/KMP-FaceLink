package io.github.kmpfacelink.internal

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.model.CameraFacing
import kotlinx.coroutines.suspendCancellableCoroutine
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

        val cameraSelector = when (cameraFacing) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        provider.unbindAll()

        if (surfaceProvider != null) {
            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
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

    fun unbindAll() {
        cameraProvider?.unbindAll()
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
        private val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        fun buildAnalysisResolutionSelector(): ResolutionSelector = resolutionSelector
    }
}
