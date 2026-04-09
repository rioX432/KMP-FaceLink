package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.mediapipe.MediaPipeWasmHandTracker
import io.github.kmpfacelink.model.HandTrackerConfig

public actual fun createHandTracker(
    platformContext: PlatformContext,
    config: HandTrackerConfig,
): HandTracker = MediaPipeWasmHandTracker(config)
