package io.github.kmpfacelink.api

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.FaceTrackerConfig

/**
 * Unsupported on Wasm/JS.
 *
 * Nothing here can load the `.task` model bundle, and re-inference only means anything when it runs
 * the same model the device ran. Recorded takes are re-inferred on Android, or through the Python
 * harness that loads the same bundle.
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
        "Offline face inference is not available on Wasm/JS: the MediaPipe model bundle cannot be " +
            "loaded here. Re-infer on Android, or through the Python harness.",
    )
