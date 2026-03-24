package io.github.kmpfacelink.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kmpfacelink.api.HandTracker

/**
 * Platform-specific camera preview composable for hand tracking.
 *
 * On Android, renders a CameraX PreviewView via AndroidView.
 * On iOS, renders an AVCaptureSession preview via UIKitView.
 */
@Composable
public expect fun HandCameraPreview(
    handTracker: HandTracker,
    modifier: Modifier,
)
