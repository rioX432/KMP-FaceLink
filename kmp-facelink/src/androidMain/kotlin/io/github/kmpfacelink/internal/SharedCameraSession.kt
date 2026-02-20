package io.github.kmpfacelink.internal

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.model.CameraFacing
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shared CameraX session that binds multiple [ImageAnalysis] use cases to a
 * single camera provider. Used by [CompositeHolisticTracker] to let multiple
 * MediaPipe landmarkers share one camera session without unbinding each other.
 */
internal class SharedCameraSession(
    private val platformContext: PlatformContext,
) {
    private val analyses = mutableListOf<ImageAnalysis>()
    private var cameraProvider: ProcessCameraProvider? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null

    fun addAnalysis(analysis: ImageAnalysis) {
        analyses.add(analysis)
    }

    fun setSurfaceProvider(provider: Preview.SurfaceProvider?) {
        surfaceProvider = provider
    }

    suspend fun start(cameraFacing: CameraFacing) {
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

        bindUseCases(provider, cameraSelector, preview)
    }

    /**
     * Binds use cases without spread operator to avoid detekt SpreadOperator warning.
     * CameraX bindToLifecycle accepts vararg UseCase, so we need different overload
     * paths depending on whether preview is present.
     */
    @Suppress("LongMethod")
    private fun bindUseCases(
        provider: ProcessCameraProvider,
        cameraSelector: CameraSelector,
        preview: Preview?,
    ) {
        val useCases = if (preview != null) {
            listOf(preview) + analyses
        } else {
            analyses.toList()
        }
        // Use CameraX UseCaseGroup to bind multiple use cases without spread
        val groupBuilder = androidx.camera.core.UseCaseGroup.Builder()
        for (useCase in useCases) {
            groupBuilder.addUseCase(useCase)
        }
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
        analyses.clear()
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
