import SwiftUI
import ARKit
import SceneKit

/// SwiftUI wrapper for ARSCNView that displays camera preview with face mesh overlay.
///
/// When an external `ARSession` is provided (from `ARKitFaceTracker.getARSession()`),
/// the view uses that session instead of creating its own — avoiding dual-session
/// conflicts on the TrueDepth camera.
struct ARCameraView: UIViewRepresentable {
    @Binding var isRunning: Bool
    @Binding var showLandmarks: Bool
    var externalSession: ARSession?

    func makeUIView(context: Context) -> ARSCNView {
        let sceneView = ARSCNView()
        sceneView.delegate = context.coordinator
        sceneView.automaticallyUpdatesLighting = true
        sceneView.showsStatistics = false
        context.coordinator.sceneView = sceneView

        // If an external session is provided, use it instead of the default one
        if let session = externalSession {
            sceneView.session = session
        }

        return sceneView
    }

    func updateUIView(_ sceneView: ARSCNView, context: Context) {
        context.coordinator.showLandmarks = showLandmarks

        // When using an external session, the tracker controls start/stop — nothing to do here.
        if externalSession != nil { return }

        if isRunning {
            startSession(sceneView)
        } else {
            sceneView.session.pause()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    private func startSession(_ sceneView: ARSCNView) {
        guard ARFaceTrackingConfiguration.isSupported else { return }

        // Allow restart after pause (removed the configuration==nil guard that blocked restarts)
        let configuration = ARFaceTrackingConfiguration()
        configuration.isLightEstimationEnabled = true
        configuration.maximumNumberOfTrackedFaces = 1
        sceneView.session.run(configuration, options: [.resetTracking, .removeExistingAnchors])
    }

    static func dismantleUIView(_ sceneView: ARSCNView, coordinator: Coordinator) {
        sceneView.session.pause()
    }

    // MARK: - Coordinator

    class Coordinator: NSObject, ARSCNViewDelegate {
        weak var sceneView: ARSCNView?
        var showLandmarks = true
        private var faceNode: SCNNode?

        func renderer(_ renderer: SCNSceneRenderer, nodeFor anchor: ARAnchor) -> SCNNode? {
            guard let faceAnchor = anchor as? ARFaceAnchor,
                  let device = sceneView?.device,
                  let faceGeometry = ARSCNFaceGeometry(device: device, fillMesh: true) else {
                return nil
            }

            let node = SCNNode(geometry: faceGeometry)
            node.geometry?.firstMaterial?.fillMode = .lines
            node.geometry?.firstMaterial?.diffuse.contents = UIColor.cyan.withAlphaComponent(0.8)
            node.geometry?.firstMaterial?.isDoubleSided = true
            node.isHidden = !showLandmarks

            faceNode = node
            return node
        }

        func renderer(_ renderer: SCNSceneRenderer, didUpdate node: SCNNode, for anchor: ARAnchor) {
            guard let faceAnchor = anchor as? ARFaceAnchor,
                  let faceGeometry = node.geometry as? ARSCNFaceGeometry else {
                return
            }

            // Update mesh with new blend shapes
            faceGeometry.update(from: faceAnchor.geometry)

            // Update visibility
            node.isHidden = !showLandmarks
        }
    }
}

#Preview {
    ARCameraView(isRunning: .constant(true), showLandmarks: .constant(true), externalSession: nil)
        .ignoresSafeArea()
}
