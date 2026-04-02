package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.NoOpFaceTracker
import io.github.kmpfacelink.internal.onnx.OnnxFaceTracker
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.util.FaceLinkLogger

private const val TAG = "FaceTrackerFactory"

public actual fun createFaceTracker(
    platformContext: PlatformContext,
    config: FaceTrackerConfig,
): FaceTracker = try {
    // Verify ONNX Runtime and JavaCV are available on the classpath
    Class.forName("ai.onnxruntime.OrtEnvironment")
    Class.forName("org.bytedeco.javacv.OpenCVFrameGrabber")
    OnnxFaceTracker(config)
} catch (expected: Exception) {
    FaceLinkLogger.w(
        TAG,
        "ONNX Runtime or JavaCV not available, falling back to NoOpFaceTracker: ${expected.message}",
    )
    NoOpFaceTracker()
}
