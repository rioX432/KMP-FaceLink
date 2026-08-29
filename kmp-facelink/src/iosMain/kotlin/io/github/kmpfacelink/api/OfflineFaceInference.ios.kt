package io.github.kmpfacelink.api

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.FaceTrackerConfig

/**
 * Unsupported on iOS.
 *
 * Face tracking here is ARKit, which only produces results from a live [ARSession] — there is no
 * way to hand it a stored image — and the library ships no MediaPipe on this platform. Re-running
 * a recorded take under a different condition is therefore an Android-side measurement.
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
        "Offline face inference is not available on iOS: ARKit cannot be given a stored image, " +
            "and this library carries no MediaPipe here. Run the re-inference on Android.",
    )
