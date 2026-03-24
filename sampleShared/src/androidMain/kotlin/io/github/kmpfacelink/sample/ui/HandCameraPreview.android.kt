package io.github.kmpfacelink.sample.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.internal.PreviewableHandTracker

/**
 * Android camera preview using CameraX PreviewView for hand tracking.
 * Only renders when the tracker implements [PreviewableHandTracker].
 */
@Composable
public actual fun HandCameraPreview(
    handTracker: HandTracker,
    modifier: Modifier,
) {
    if (handTracker is PreviewableHandTracker) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    handTracker.setSurfaceProvider(surfaceProvider)
                }
            },
            modifier = modifier,
        )
    }
}
