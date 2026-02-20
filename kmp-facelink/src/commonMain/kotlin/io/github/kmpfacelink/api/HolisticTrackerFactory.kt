package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.HolisticTrackerConfig

/**
 * Platform-specific factory for creating [HolisticTracker] instances.
 *
 * Each platform (Android/iOS) provides its own implementation via `expect`/`actual`.
 * The holistic tracker composes individual face, hand, and body trackers with a shared
 * camera session for simultaneous multi-modality tracking.
 */
public expect fun createHolisticTracker(
    platformContext: PlatformContext,
    config: HolisticTrackerConfig = HolisticTrackerConfig(),
): HolisticTracker
