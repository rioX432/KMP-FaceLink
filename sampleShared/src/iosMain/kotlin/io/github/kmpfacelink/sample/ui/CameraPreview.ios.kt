@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("DEPRECATION")

package io.github.kmpfacelink.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.getARSession
import platform.ARKit.ARSCNView

/**
 * iOS camera preview using ARSCNView connected to the tracker's ARSession.
 * Shares the tracker's session to avoid dual-session conflicts on the TrueDepth camera.
 */
@Composable
public actual fun FaceCameraPreview(
    faceTracker: FaceTracker,
    modifier: Modifier,
) {
    val arSession = faceTracker.getARSession()
    UIKitView(
        factory = {
            ARSCNView().apply {
                automaticallyUpdatesLighting = true
                if (arSession != null) {
                    setSession(arSession)
                }
            }
        },
        modifier = modifier,
        update = { sceneView ->
            // Update session if it changed (e.g., after tracker restart)
            val currentSession = faceTracker.getARSession()
            if (currentSession != null) {
                sceneView.setSession(currentSession)
            }
        },
    )
}
