package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.CompositeHolisticTracker
import io.github.kmpfacelink.model.HolisticTrackerConfig

public actual fun createHolisticTracker(
    platformContext: PlatformContext,
    config: HolisticTrackerConfig,
): HolisticTracker = CompositeHolisticTracker(platformContext, config)
