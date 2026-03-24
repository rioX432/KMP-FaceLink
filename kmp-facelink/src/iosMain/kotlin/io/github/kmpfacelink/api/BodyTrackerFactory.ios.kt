package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.VisionBodyTracker
import io.github.kmpfacelink.internal.VisionBodyTracker3D
import io.github.kmpfacelink.model.BodyTrackerConfig

public actual fun createBodyTracker(
    platformContext: PlatformContext,
    config: BodyTrackerConfig,
): BodyTracker = if (config.enable3D) {
    VisionBodyTracker3D(config)
} else {
    VisionBodyTracker(config)
}
