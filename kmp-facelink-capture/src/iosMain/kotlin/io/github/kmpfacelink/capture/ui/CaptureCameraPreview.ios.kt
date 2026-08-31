@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.capture.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import io.github.kmpfacelink.capture.CaptureSession
import io.github.kmpfacelink.capture.IosCaptureSession

/**
 * Hosts the Swift-owned native AR preview inside the common capture layout.
 *
 * Swift retains and owns the ARSCNView, its ARSession, the renderer delegate, native face mesh,
 * and all ARKit callback values. KMP receives only this opaque UIKit host to place beneath the
 * guide; it never creates, runs, pauses, delegates, or inspects an ARKit object. The native mesh
 * is visual-only: iOS recordings keep `landmarks = null` because ARKit topology is not the common
 * MediaPipe 478-point contract.
 */
@Composable
internal actual fun CaptureCameraPreview(
    session: CaptureSession,
    contentScale: PreviewContentScale,
    modifier: Modifier,
) {
    val iosSession = session as? IosCaptureSession ?: return
    require(contentScale == PreviewContentScale.FILL) {
        "the Swift ARSCNView preview uses the guided FILL camera viewport"
    }
    // The runtime holds this UIView strongly across Compose disposal/recomposition. UIKitView only
    // embeds it; its release must not pause the shared session or detach Swift's renderer delegate.
    val preview = remember(iosSession) { iosSession.attachedPreviewView() }
    UIKitView(
        factory = { preview },
        modifier = modifier,
    )
}
