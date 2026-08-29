package io.github.kmpfacelink.api

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.FaceTrackerConfig

/**
 * Unsupported on the JVM target.
 *
 * The desktop face tracker here is ONNX Runtime with its own models, not MediaPipe, so running a
 * recorded take through it would answer a different question than the one re-inference is asked:
 * what *this* model does with those pixels. Host-side re-inference of a take therefore goes through
 * the Python harness, which loads the same `.task` bundle the device does.
 *
 * It throws rather than returning empty results, so a caller finds out while writing the harness
 * instead of after a recording session has produced a directory of nothing.
 */
@ExperimentalFaceLinkApi
public actual fun openOfflineFaceInference(
    platformContext: PlatformContext,
    config: FaceTrackerConfig,
): OfflineFaceInference =
    throw UnsupportedOperationException(
        "Offline face inference is not available on the JVM target: the desktop tracker is ONNX " +
            "Runtime rather than MediaPipe, so it would not reproduce the device's model. Re-infer " +
            "on Android, or through the Python harness that loads the same model bundle.",
    )
