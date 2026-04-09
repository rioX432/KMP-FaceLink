package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.mediapipe.MediaPipeWasmBodyTracker
import io.github.kmpfacelink.model.BodyTrackerConfig

public actual fun createBodyTracker(
    platformContext: PlatformContext,
    config: BodyTrackerConfig,
): BodyTracker = MediaPipeWasmBodyTracker(config)
