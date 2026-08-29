package io.github.kmpfacelink.model

/** Estimator family that produced a face-tracking observation. */
public enum class FaceTrackingBackend {
    /** Apple ARKit `ARFaceAnchor` blend shapes. */
    ARKIT,

    /** MediaPipe Face Landmarker blend shapes. */
    MEDIAPIPE,

    /** ONNX Runtime inference over the desktop model. */
    ONNX,
}
