package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.MediaPipeHandTracker
import io.github.kmpfacelink.model.HandTrackerConfig

public actual fun createHandTracker(
    platformContext: PlatformContext,
    config: HandTrackerConfig,
): HandTracker = MediaPipeHandTracker(platformContext, config)
