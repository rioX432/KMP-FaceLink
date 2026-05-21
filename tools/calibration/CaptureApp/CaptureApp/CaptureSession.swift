import ARKit
import Combine

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
    @Published var trackingState: String = "Not started"

    // MARK: - Captured Data

    private(set) var capturedFrames: [CapturedFrame] = []

    // MARK: - Private

    let arSession = ARSession()
    private var recordingStartTime: TimeInterval = 0
    private var displayLink: CADisplayLink?

    // MARK: - Lifecycle

    override init() {
        super.init()
        arSession.delegate = self
    }

    // MARK: - Tracking Control

    func startTracking() {
        guard ARFaceTrackingConfiguration.isSupported else {
            trackingState = "TrueDepth camera not available"
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

    // MARK: - Pixel Buffer Copy

    /// Deep-copy a CVPixelBuffer so the original can be recycled by ARKit.
    private static func copyPixelBuffer(_ source: CVPixelBuffer) -> CVPixelBuffer? {
        let width = CVPixelBufferGetWidth(source)
        let height = CVPixelBufferGetHeight(source)
        let pixelFormat = CVPixelBufferGetPixelFormatType(source)

        var destination: CVPixelBuffer?
        let status = CVPixelBufferCreate(
            kCFAllocatorDefault,
            width,
            height,
            pixelFormat,
            nil,
            &destination
        )
        guard status == kCVReturnSuccess, let dest = destination else {
            return nil
        }

        CVPixelBufferLockBaseAddress(source, .readOnly)
        CVPixelBufferLockBaseAddress(dest, [])
        defer {
            CVPixelBufferUnlockBaseAddress(source, .readOnly)
            CVPixelBufferUnlockBaseAddress(dest, [])
        }

        let planeCount = CVPixelBufferGetPlaneCount(source)
        if planeCount > 0 {
            for plane in 0..<planeCount {
                guard let srcAddr = CVPixelBufferGetBaseAddressOfPlane(source, plane),
                      let dstAddr = CVPixelBufferGetBaseAddressOfPlane(dest, plane) else {
                    continue
                }
                let srcStride = CVPixelBufferGetBytesPerRowOfPlane(source, plane)
                let dstStride = CVPixelBufferGetBytesPerRowOfPlane(dest, plane)
                let height = CVPixelBufferGetHeightOfPlane(source, plane)
                let rowBytes = min(srcStride, dstStride)
                for row in 0..<height {
                    memcpy(
                        dstAddr.advanced(by: row * dstStride),
                        srcAddr.advanced(by: row * srcStride),
                        rowBytes
                    )
                }
            }
        } else {
            guard let srcAddr = CVPixelBufferGetBaseAddress(source),
                  let dstAddr = CVPixelBufferGetBaseAddress(dest) else {
                return nil
            }
            let srcStride = CVPixelBufferGetBytesPerRow(source)
            let dstStride = CVPixelBufferGetBytesPerRow(dest)
            let rowBytes = min(srcStride, dstStride)
            for row in 0..<height {
                memcpy(
                    dstAddr.advanced(by: row * dstStride),
                    srcAddr.advanced(by: row * srcStride),
                    rowBytes
                )
            }
        }

        return dest
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

        // Deep-copy the pixel buffer because ARKit recycles it on the next frame.
        guard let copiedBuffer = Self.copyPixelBuffer(currentFrame.capturedImage) else {
            return
        }

        let frame = CapturedFrame(
            timestamp: currentFrame.timestamp,
            blendShapes: faceAnchor.blendShapes,
            pixelBuffer: copiedBuffer
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
