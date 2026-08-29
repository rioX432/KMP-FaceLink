import ARKit
import Combine
import CoreImage
import ImageIO

/// Manages ARKit face tracking and dual recording of blendshapes + RGB frames.
///
/// Usage:
/// ```swift
/// let session = CaptureSession()
/// session.startTracking()   // Begin ARKit face tracking
/// session.startRecording()  // Begin capturing frames
/// // ... user performs expressions ...
/// session.stopRecording()   // Stop capturing
/// session.stopTracking()    // Pause ARKit
/// let frames = session.capturedFrames  // Access recorded data
/// ```
final class CaptureSession: NSObject, ObservableObject {

    // MARK: - Published State

    @Published var isTracking = false
    @Published var isRecording = false
    @Published var frameCount = 0
    @Published var elapsedTime: TimeInterval = 0
    @Published var trackingState: String = ARFaceTrackingConfiguration.isSupported
        ? "Face tracking supported — not started"
        : "ARKit face tracking is not supported"

    let isFaceTrackingSupported = ARFaceTrackingConfiguration.isSupported

    // MARK: - Captured Data

    private(set) var capturedFrames: [CapturedFrame] = []

    // MARK: - Private

    let arSession = ARSession()
    private var recordingStartTime: TimeInterval = 0
    private var displayLink: CADisplayLink?
    private var lastCapturedTimestamp: TimeInterval?

    private static let targetCaptureFps = 30.0
    private static let minimumFrameInterval = 1.0 / targetCaptureFps
    private static let jpegQuality: CGFloat = 0.92
    private static let ciContext = CIContext()

    // MARK: - Lifecycle

    override init() {
        super.init()
        arSession.delegate = self
        print("ARFaceTrackingConfiguration.isSupported=\(isFaceTrackingSupported)")
    }

    // MARK: - Tracking Control

    func startTracking() {
        guard isFaceTrackingSupported else {
            trackingState = "ARKit face tracking is not supported"
            return
        }

        let configuration = ARFaceTrackingConfiguration()
        configuration.isLightEstimationEnabled = true
        configuration.maximumNumberOfTrackedFaces = 1

        arSession.run(configuration, options: [.resetTracking, .removeExistingAnchors])
        isTracking = true
        trackingState = "Tracking active"
    }

    func stopTracking() {
        arSession.pause()
        isTracking = false
        trackingState = "Tracking paused"
        stopElapsedTimer()
    }

    // MARK: - Recording Control

    func startRecording() {
        guard isTracking else { return }

        capturedFrames.removeAll()
        frameCount = 0
        elapsedTime = 0
        recordingStartTime = CACurrentMediaTime()
        lastCapturedTimestamp = nil
        isRecording = true

        startElapsedTimer()
    }

    func stopRecording() {
        isRecording = false
        stopElapsedTimer()
    }

    /// Discard all captured frames and reset counters.
    func resetSession() {
        stopRecording()
        capturedFrames.removeAll()
        frameCount = 0
        elapsedTime = 0
    }

    // MARK: - Elapsed Timer

    private func startElapsedTimer() {
        let link = CADisplayLink(target: self, selector: #selector(updateElapsed))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 10, maximum: 15)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    private func stopElapsedTimer() {
        displayLink?.invalidate()
        displayLink = nil
    }

    @objc private func updateElapsed() {
        guard isRecording else { return }
        elapsedTime = CACurrentMediaTime() - recordingStartTime
    }

    // MARK: - Frame Encoding

    /// Encode while the ARFrame still owns the pixel buffer. The resulting
    /// Data is independent of ARKit's buffer lifecycle and is much smaller
    /// than retaining an uncompressed CVPixelBuffer for every frame.
    private static func encodeJPEG(_ pixelBuffer: CVPixelBuffer) -> Data? {
        let image = CIImage(cvPixelBuffer: pixelBuffer).oriented(.right)
        guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else {
            return nil
        }
        let qualityKey = CIImageRepresentationOption(
            rawValue: kCGImageDestinationLossyCompressionQuality as String
        )
        return ciContext.jpegRepresentation(
            of: image,
            colorSpace: colorSpace,
            options: [qualityKey: jpegQuality]
        )
    }
}

// MARK: - ARSessionDelegate

extension CaptureSession: ARSessionDelegate {

    func session(_ session: ARSession, didUpdate anchors: [ARAnchor]) {
        guard isRecording else { return }
        guard let faceAnchor = anchors.first(where: { $0 is ARFaceAnchor }) as? ARFaceAnchor else {
            return
        }
        guard let currentFrame = session.currentFrame else { return }

        if let previous = lastCapturedTimestamp,
           currentFrame.timestamp - previous < Self.minimumFrameInterval {
            return
        }

        guard let jpegData = Self.encodeJPEG(currentFrame.capturedImage) else {
            return
        }
        lastCapturedTimestamp = currentFrame.timestamp

        let frame = CapturedFrame(
            timestamp: currentFrame.timestamp,
            blendShapes: faceAnchor.blendShapes,
            jpegData: jpegData
        )

        DispatchQueue.main.async { [weak self] in
            guard let self, self.isRecording else { return }
            self.capturedFrames.append(frame)
            self.frameCount = self.capturedFrames.count
        }
    }

    func session(_ session: ARSession, cameraDidChangeTrackingState camera: ARCamera) {
        switch camera.trackingState {
        case .notAvailable:
            trackingState = "Not available"
        case .limited(let reason):
            trackingState = limitedReasonDescription(reason)
        case .normal:
            trackingState = "Normal"
        }
    }

    func session(_ session: ARSession, didFailWithError error: Error) {
        trackingState = "Error: \(error.localizedDescription)"
        isRecording = false
        isTracking = false
    }

    private func limitedReasonDescription(
        _ reason: ARCamera.TrackingState.Reason
    ) -> String {
        switch reason {
        case .initializing:
            return "Initializing..."
        case .excessiveMotion:
            return "Excessive motion"
        case .insufficientFeatures:
            return "Insufficient features"
        case .relocalizing:
            return "Relocalizing..."
        @unknown default:
            return "Limited (unknown)"
        }
    }
}
