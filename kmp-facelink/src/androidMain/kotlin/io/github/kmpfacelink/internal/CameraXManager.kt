package io.github.kmpfacelink.internal

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
        private const val ANALYSIS_WIDTH = 640
        private const val ANALYSIS_HEIGHT = 480

        private val analysisResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

        private val previewResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        fun buildAnalysisResolutionSelector(): ResolutionSelector = analysisResolutionSelector

        fun buildPreviewResolutionSelector(): ResolutionSelector = previewResolutionSelector
    }
}
