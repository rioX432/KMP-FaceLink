package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.NoOpHolisticTracker
import io.github.kmpfacelink.model.HolisticTrackerConfig

public actual fun createHolisticTracker(
    platformContext: PlatformContext,
    config: HolisticTrackerConfig,
): HolisticTracker = NoOpHolisticTracker()
