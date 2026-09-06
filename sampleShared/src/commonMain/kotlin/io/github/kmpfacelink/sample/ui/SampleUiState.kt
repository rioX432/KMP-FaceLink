package io.github.kmpfacelink.sample.ui

import io.github.kmpfacelink.model.SmoothingConfig

internal const val LANDMARK_OVERLAY_UNAVAILABLE_TEXT =
    "Normalized landmark overlay unavailable from the current adapter"

internal enum class LandmarkOverlayState {
    HIDDEN,
    AVAILABLE,
    CURRENT_ADAPTER_UNAVAILABLE,
}

internal fun landmarkOverlayState(
    isTracking: Boolean,
    hasNormalizedLandmarks: Boolean,
): LandmarkOverlayState = when {
    !isTracking -> LandmarkOverlayState.HIDDEN
    hasNormalizedLandmarks -> LandmarkOverlayState.AVAILABLE
    else -> LandmarkOverlayState.CURRENT_ADAPTER_UNAVAILABLE
}

internal enum class SampleSmoothingOption(
    val label: String,
) {
    NONE("None"),
    EMA("EMA"),
    ONE_EURO("1Euro"),
    ;

    fun toConfig(): SmoothingConfig = when (this) {
        NONE -> SmoothingConfig.None
        EMA -> SmoothingConfig.Ema()
        ONE_EURO -> SmoothingConfig.OneEuro()
    }
}

internal fun sampleSmoothingOption(config: SmoothingConfig): SampleSmoothingOption = when (config) {
    SmoothingConfig.None -> SampleSmoothingOption.NONE
    is SmoothingConfig.Ema -> SampleSmoothingOption.EMA
    is SmoothingConfig.OneEuro -> SampleSmoothingOption.ONE_EURO
    else -> error("The sample UI does not expose ${config::class.simpleName}")
}
