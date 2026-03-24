package io.github.kmpfacelink.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kmpfacelink.api.FaceTracker

/**
 * Platform-specific camera preview composable for face tracking.
 *
 * On Android, renders a CameraX PreviewView via AndroidView.
 * On iOS, renders an ARSCNView via UIKitView.
 */
@Composable
public expect fun FaceCameraPreview(
    faceTracker: FaceTracker,
    modifier: Modifier,
)
