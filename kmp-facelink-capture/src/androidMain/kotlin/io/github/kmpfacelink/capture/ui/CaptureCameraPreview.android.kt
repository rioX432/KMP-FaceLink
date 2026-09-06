package io.github.kmpfacelink.capture.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kmpfacelink.capture.AndroidCaptureSession
import io.github.kmpfacelink.capture.CaptureSession

/**
 * A display-only surface for [AndroidCaptureSession]'s already-bound front camera.
 *
 * The session binds this surface next to its existing analysis use case. It neither opens another
 * camera nor reads, transforms, stores, or submits preview pixels to inference.
 */
@Composable
internal actual fun CaptureCameraPreview(
    session: CaptureSession,
    contentScale: PreviewContentScale,
    modifier: Modifier,
) {
    val androidSession = session as? AndroidCaptureSession ?: return
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            previewView.scaleType = when (contentScale) {
                PreviewContentScale.FIT -> PreviewView.ScaleType.FIT_CENTER
                PreviewContentScale.FILL -> PreviewView.ScaleType.FILL_CENTER
            }
            androidSession.attachPreview(previewView.surfaceProvider)
        },
        onRelease = { previewView -> androidSession.detachPreview(previewView.surfaceProvider) },
    )
}
