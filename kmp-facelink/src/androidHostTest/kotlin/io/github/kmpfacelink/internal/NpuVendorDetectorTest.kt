package io.github.kmpfacelink.internal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NpuVendorDetectorTest {

    // -- isQualcomm tests --

    @Test
    fun isQualcommDetectsQcomHardware() {
        assertTrue(NpuVendorDetector.isQualcomm("qcom"))
    }

    @Test
    fun isQualcommDetectsQcomPrefix() {
        assertTrue(NpuVendorDetector.isQualcomm("qcom_lahaina"))
    }

    @Test
    fun isQualcommDetectsSnapdragonHardware() {
        assertTrue(NpuVendorDetector.isQualcomm("snapdragon"))
    }

    @Test
    fun isQualcommReturnsFalseForMediaTek() {
        assertFalse(NpuVendorDetector.isQualcomm("mt6893"))
    }

    @Test
    fun isQualcommReturnsFalseForEmpty() {
        assertFalse(NpuVendorDetector.isQualcomm(""))
    }

    @Test
    fun isQualcommReturnsFalseForUnknown() {
        assertFalse(NpuVendorDetector.isQualcomm("exynos"))
    }

    // -- isMediaTek tests --

    @Test
    fun isMediaTekDetectsMtPrefix() {
        assertTrue(NpuVendorDetector.isMediaTek("mt6893"))
    }

    @Test
    fun isMediaTekDetectsMediatekString() {
        assertTrue(NpuVendorDetector.isMediaTek("mediatek"))
    }

    @Test
    fun isMediaTekDetectsMediatekContained() {
        assertTrue(NpuVendorDetector.isMediaTek("device_mediatek_v2"))
    }

    @Test
    fun isMediaTekReturnsFalseForQualcomm() {
        assertFalse(NpuVendorDetector.isMediaTek("qcom"))
    }

    @Test
    fun isMediaTekReturnsFalseForEmpty() {
        assertFalse(NpuVendorDetector.isMediaTek(""))
    }

    @Test
    fun isMediaTekReturnsFalseForUnknown() {
        assertFalse(NpuVendorDetector.isMediaTek("exynos"))
    }

    @Test
    fun isMediaTekReturnsFalseForAmtPrefix() {
        // "amt" starts with "mt" at index 1, but startsWith checks index 0
        assertFalse(NpuVendorDetector.isMediaTek("amt123"))
    }

    // detect() depends on android.os.Build which requires Robolectric shadow setup.
    // The pure string-matching logic (isQualcomm/isMediaTek) is tested above.
}
