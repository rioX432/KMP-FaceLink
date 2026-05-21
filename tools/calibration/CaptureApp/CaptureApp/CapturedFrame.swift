import ARKit
import CoreVideo

/// A single captured frame containing ARKit blendshapes and the front camera image.
struct CapturedFrame {
    /// ARSession timestamp (seconds since session start).
    let timestamp: Double

    /// ARKit face blendshape coefficients for this frame.
    let blendShapes: [ARFaceAnchor.BlendShapeLocation: NSNumber]

    /// Front-camera RGB pixel buffer captured at the same instant.
    let pixelBuffer: CVPixelBuffer
}
