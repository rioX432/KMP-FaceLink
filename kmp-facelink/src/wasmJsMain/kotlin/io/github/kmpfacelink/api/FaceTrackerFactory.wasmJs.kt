package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.mediapipe.MediaPipeWasmFaceTracker
import io.github.kmpfacelink.model.FaceTrackerConfig

public actual fun createFaceTracker(
    platformContext: PlatformContext,
    config: FaceTrackerConfig,
): FaceTracker = MediaPipeWasmFaceTracker(config)
