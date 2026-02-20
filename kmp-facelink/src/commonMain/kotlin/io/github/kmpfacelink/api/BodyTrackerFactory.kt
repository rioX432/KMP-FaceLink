package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.BodyTrackerConfig

/**
 * Platform-specific factory for creating [BodyTracker] instances.
 *
 * Each platform (Android/iOS) provides its own implementation via `expect`/`actual`.
 */
public expect fun createBodyTracker(
    platformContext: PlatformContext,
    config: BodyTrackerConfig = BodyTrackerConfig(),
): BodyTracker
