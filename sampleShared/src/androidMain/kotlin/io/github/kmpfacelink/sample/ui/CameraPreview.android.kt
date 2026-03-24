package io.github.kmpfacelink.sample.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.internal.PreviewableFaceTracker

/**
 * Android camera preview using CameraX PreviewView.
 * Only renders when the tracker implements [PreviewableFaceTracker].
 */
@Composable
public actual fun FaceCameraPreview(
    faceTracker: FaceTracker,
    modifier: Modifier,
) {
    if (faceTracker is PreviewableFaceTracker) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    faceTracker.setSurfaceProvider(surfaceProvider)
                }
            },
            modifier = modifier,
        )
    }
}
