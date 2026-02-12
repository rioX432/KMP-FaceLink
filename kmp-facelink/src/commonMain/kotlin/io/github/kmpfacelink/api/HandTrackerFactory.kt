package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.HandTrackerConfig

/**
 * Platform-specific factory for creating [HandTracker] instances.
 *
 * Each platform (Android/iOS) provides its own implementation via `expect`/`actual`.
 */
public expect fun createHandTracker(
    platformContext: PlatformContext,
    config: HandTrackerConfig = HandTrackerConfig(),
): HandTracker
