package io.github.kmpfacelink.sample.ui

import io.github.kmpfacelink.model.SmoothingConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleUiStateTest {
    @Test
    fun initialSmoothingSelectionMatchesActualConfig() {
        assertEquals(SampleSmoothingOption.NONE, sampleSmoothingOption(SmoothingConfig.None))
        assertEquals(SampleSmoothingOption.EMA, sampleSmoothingOption(SmoothingConfig.Ema(alpha = 0.4f)))
        assertEquals(SampleSmoothingOption.ONE_EURO, sampleSmoothingOption(SmoothingConfig.OneEuro()))
    }

    @Test
    fun overlayIsExplicitWhenCurrentAdapterHasNoNormalizedLandmarks() {
        assertEquals(LandmarkOverlayState.HIDDEN, landmarkOverlayState(isTracking = false, hasNormalizedLandmarks = false))
        assertEquals(LandmarkOverlayState.AVAILABLE, landmarkOverlayState(isTracking = true, hasNormalizedLandmarks = true))
        assertEquals(
            LandmarkOverlayState.CURRENT_ADAPTER_UNAVAILABLE,
            landmarkOverlayState(isTracking = true, hasNormalizedLandmarks = false),
        )
    }
}
