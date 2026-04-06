package io.github.kmpfacelink.internal

import android.os.Build
import io.github.kmpfacelink.model.NpuVendor
import io.github.kmpfacelink.util.FaceLinkLogger

private const val TAG = "NpuVendorDetector"

/**
 * Detects the NPU vendor on the current Android device based on SoC identifiers.
 *
 * Supports:
 * - **Qualcomm**: Snapdragon SoCs with QNN / AI Engine Direct (8 Gen 1+, 7 Gen 3+)
 * - **MediaTek**: Dimensity SoCs with NeuroPilot (7300+)
 *
 * Detection uses [Build.HARDWARE] and [Build.SOC_MODEL] (API 31+) to identify
 * the chipset vendor. This does not guarantee that the LiteRT vendor delegate
 * library is available on the device — delegate initialization may still fail
 * at runtime, triggering the fallback chain.
 */
internal object NpuVendorDetector {

    /**
     * Detects the NPU vendor for the current device.
     *
     * @return The detected [NpuVendor], or [NpuVendor.NONE] if no supported vendor is found.
     */
    fun detect(): NpuVendor {
        val vendor = detectFromSocInfo()
        FaceLinkLogger.d(TAG, "Detected NPU vendor: $vendor (hw=${Build.HARDWARE})")
        return vendor
    }

    private fun detectFromSocInfo(): NpuVendor {
        val hardware = Build.HARDWARE.lowercase()

        // Qualcomm SoCs typically have "qcom" or "snapdragon" in hardware string
        if (isQualcomm(hardware)) return NpuVendor.QUALCOMM

        // MediaTek SoCs typically have "mt" prefix in hardware string
        if (isMediaTek(hardware)) return NpuVendor.MEDIATEK

        // API 31+ provides explicit SoC model info
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return detectFromSocModel()
        }

        return NpuVendor.NONE
    }

    private fun detectFromSocModel(): NpuVendor {
        val socModel = Build.SOC_MODEL.lowercase()
        return when {
            socModel.contains("sm") || socModel.contains("qcom") -> NpuVendor.QUALCOMM
            socModel.contains("mt") || socModel.contains("dimensity") -> NpuVendor.MEDIATEK
            else -> NpuVendor.NONE
        }
    }

    internal fun isQualcomm(hardware: String): Boolean =
        hardware.contains("qcom") || hardware.contains("snapdragon")

    internal fun isMediaTek(hardware: String): Boolean =
        hardware.startsWith("mt") || hardware.contains("mediatek")
}
