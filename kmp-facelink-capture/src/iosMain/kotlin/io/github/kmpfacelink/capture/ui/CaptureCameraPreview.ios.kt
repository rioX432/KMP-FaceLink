@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.capture.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import io.github.kmpfacelink.capture.CaptureSession
import io.github.kmpfacelink.capture.IosCaptureSession
import kotlinx.cinterop.ObjCSignatureOverride
import platform.ARKit.ARAnchor
import platform.ARKit.ARFaceAnchor
import platform.ARKit.ARSCNFaceGeometry
import platform.ARKit.ARSCNView
import platform.ARKit.ARSCNViewDelegateProtocol
import platform.SceneKit.SCNNode
import platform.SceneKit.SCNSceneRendererProtocol
import platform.SceneKit.SCNFillModeLines
import platform.UIKit.UIColor
import platform.darwin.NSObject

/**
 * Render-only participant preview for the ARSession already owned by [IosCaptureSession].
 *
 * This view never creates, runs, pauses, or delegates an ARSession. Its sole effect is attaching
 * the session to ARSCNView so the guided capture UI can show the native camera feed and ARKit's
 * native face geometry under its guide. Capture continues to own the camera lease and lifecycle.
 *
 * The mesh is not [CaptureUiState.landmarks]: iOS recording deliberately retains `landmarks =
 * null`, because ARKit face topology is not MediaPipe's 478-point topology. The visual mesh stays
 * in the native renderer and is neither stored nor fed to recording/inference.
 */
@Composable
internal actual fun CaptureCameraPreview(
    session: CaptureSession,
    contentScale: PreviewContentScale,
    modifier: Modifier,
) {
    val iosSession = session as? IosCaptureSession ?: return
    // UIKit's delegate is weak. Compose retains this binding while the native view is composed.
    val rendererBinding = remember { FaceMeshRendererBinding() }
    UIKitView(
        factory = {
            ARSCNView().apply {
                rendererBinding.attach(this, iosSession.previewArSession(), contentScale)
            }
        },
        modifier = modifier,
        update = { view -> rendererBinding.attach(view, iosSession.previewArSession(), contentScale) },
        onRelease = { view -> rendererBinding.detach(view) },
    )
}

/**
 * Keeps its SceneKit delegate strongly while it displays a session owned elsewhere.
 *
 * [detach] intentionally does not pause or replace [ARSCNView.session]; doing either
 * would mutate the capture session's lifecycle. Releasing the view only detaches this renderer's
 * own delegate.
 */
private class FaceMeshRendererBinding {
    private var meshDelegate: NativeFaceMeshDelegate? = null

    fun attach(
        view: ARSCNView,
        session: platform.ARKit.ARSession,
        contentScale: PreviewContentScale,
    ) {
        if (meshDelegate == null) meshDelegate = NativeFaceMeshDelegate(view)
        view.automaticallyUpdatesLighting = true
        view.rendersCameraGrain = false
        // ARSCNView's camera background fills its bounds. Make the common contract explicit: it
        // matches the guided FILL viewport; other values are deliberately not silently remapped.
        require(contentScale == PreviewContentScale.FILL) {
            "ARSCNView uses the guided FILL camera viewport"
        }
        view.delegate = meshDelegate
        // Recomposition must not repeatedly alter the externally owned session binding.
        if (view.session !== session) view.setSession(session)
    }

    fun detach(view: ARSCNView) {
        view.delegate = null
        meshDelegate = null
    }
}

/** Renders ARKit's native topology only; it does not touch ARSession's capture delegate. */
private class NativeFaceMeshDelegate(
    private val sceneView: ARSCNView,
) : NSObject(), ARSCNViewDelegateProtocol {
    @ObjCSignatureOverride
    override fun renderer(renderer: SCNSceneRendererProtocol, nodeForAnchor: ARAnchor): SCNNode? {
        val faceAnchor = nodeForAnchor as? ARFaceAnchor ?: return null
        val device = sceneView.device ?: return null
        val geometry = ARSCNFaceGeometry.faceGeometryWithDevice(device, fillMesh = true) ?: return null
        geometry.firstMaterial?.apply {
            fillMode = SCNFillModeLines
            diffuse.contents = UIColor.cyanColor.colorWithAlphaComponent(0.8)
            doubleSided = true
        }
        return SCNNode().also { node ->
            node.geometry = geometry
            geometry.updateFromFaceGeometry(faceAnchor.geometry)
            node.setHidden(false)
        }
    }

    @ObjCSignatureOverride
    override fun renderer(
        renderer: SCNSceneRendererProtocol,
        didUpdateNode: SCNNode,
        forAnchor: ARAnchor,
    ) {
        val faceAnchor = forAnchor as? ARFaceAnchor ?: return
        val geometry = didUpdateNode.geometry as? ARSCNFaceGeometry ?: return
        geometry.updateFromFaceGeometry(faceAnchor.geometry)
        didUpdateNode.setHidden(false)
    }
}
