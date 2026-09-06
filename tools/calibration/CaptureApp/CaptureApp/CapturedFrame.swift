import ARKit

/// A single captured frame containing ARKit blendshapes and the front camera image.
struct CapturedFrame {
    /// ARSession timestamp (seconds since session start).
    let timestamp: Double

    /// ARKit face blendshape coefficients for this frame.
    let blendShapes: [ARFaceAnchor.BlendShapeLocation: NSNumber]

    /// JPEG-encoded front-camera RGB frame captured at the same instant.
    ///
    /// Keeping compressed bytes instead of a full CVPixelBuffer prevents a
    /// short calibration clip from consuming several gigabytes of memory.
    let jpegData: Data
}
