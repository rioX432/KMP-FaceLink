package io.github.kmpfacelink.internal.depth

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Device-independent tests for the depth preprocessing math. The interpreter/GPU path requires
 * a real device + model and is validated separately during on-device benchmarking (#898).
 */
class DepthImagePreprocessorTest {

    private val tolerance = 1e-5f

    @Test
    fun normalizeRgbExtractsChannelsInRgbOrder() {
        // ARGB pixel with distinct channels: R=32, G=64, B=96.
        val pixel = 0xFF204060.toInt()
        val result = DepthImagePreprocessor.normalizeRgb(pixel, DepthNormalization.UNIT)
        assertEquals(32f / 255f, result[0], tolerance)
        assertEquals(64f / 255f, result[1], tolerance)
        assertEquals(96f / 255f, result[2], tolerance)
    }

    @Test
    fun normalizeRgbUnitWhiteIsOne() {
        val result = DepthImagePreprocessor.normalizeRgb(0xFFFFFFFF.toInt(), DepthNormalization.UNIT)
        assertEquals(1f, result[0], tolerance)
        assertEquals(1f, result[1], tolerance)
        assertEquals(1f, result[2], tolerance)
    }

    @Test
    fun normalizeRgbUnitBlackIsZero() {
        val result = DepthImagePreprocessor.normalizeRgb(0xFF000000.toInt(), DepthNormalization.UNIT)
        assertEquals(0f, result[0], tolerance)
        assertEquals(0f, result[1], tolerance)
        assertEquals(0f, result[2], tolerance)
    }

    @Test
    fun normalizeRgbAppliesImagenetMeanStd() {
        val result = DepthImagePreprocessor.normalizeRgb(0xFFFFFFFF.toInt(), DepthNormalization.IMAGENET)
        assertEquals((1f - 0.485f) / 0.229f, result[0], tolerance)
        assertEquals((1f - 0.456f) / 0.224f, result[1], tolerance)
        assertEquals((1f - 0.406f) / 0.225f, result[2], tolerance)
    }
}
