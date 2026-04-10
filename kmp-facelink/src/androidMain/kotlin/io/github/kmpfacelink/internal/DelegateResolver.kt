package io.github.kmpfacelink.internal

import com.google.mediapipe.tasks.core.Delegate
import io.github.kmpfacelink.model.InferenceDelegate
import io.github.kmpfacelink.model.NpuVendor
import io.github.kmpfacelink.util.FaceLinkLogger

private const val TAG = "DelegateResolver"

/**
 * Resolves an [InferenceDelegate] preference into an ordered list of MediaPipe [Delegate]
 * values to try, implementing a fallback chain.
 *
 * The caller should iterate through the returned list, attempting to create the landmarker
 * with each delegate until one succeeds.
 */
internal object DelegateResolver {

    /**
     * Returns an ordered fallback chain of [Delegate] values for the given [InferenceDelegate].
     *
     * - [InferenceDelegate.AUTO] / [InferenceDelegate.GPU]: GPU -> CPU
     * - [InferenceDelegate.NPU]: logs NPU readiness, then GPU -> CPU
     *   (NPU delegate pending MediaPipe support — will be prepended when available)
     * - [InferenceDelegate.CPU]: CPU only
     */
    fun resolve(preference: InferenceDelegate): List<Delegate> {
        return when (preference) {
            InferenceDelegate.AUTO -> listOf(Delegate.GPU, Delegate.CPU)
            InferenceDelegate.GPU -> listOf(Delegate.GPU, Delegate.CPU)
            InferenceDelegate.CPU -> listOf(Delegate.CPU)
            InferenceDelegate.NPU -> resolveNpu()
        }
    }

    /**
     * Resolves the NPU delegate fallback chain.
     *
     * Detects the device's NPU vendor and logs readiness status. MediaPipe Tasks
     * Vision does not yet expose an NPU delegate value in its [Delegate] enum.
     * When MediaPipe adds NPU support (expected mid-2026), this method will
     * prepend the NPU delegate at the head of the chain.
     *
     * Until then, NPU preference falls back to GPU -> CPU, but the vendor
     * detection confirms whether the device *would* benefit from NPU once
     * the delegate becomes available.
     */
    private fun resolveNpu(): List<Delegate> {
        val vendor = NpuVendorDetector.detect()
        if (vendor != NpuVendor.NONE) {
            FaceLinkLogger.d(
                TAG,
                "NPU requested — $vendor NPU detected. " +
                    "MediaPipe NPU delegate not yet available; using GPU -> CPU. " +
                    "NPU will be used automatically when MediaPipe ships the delegate.",
            )
        } else {
            FaceLinkLogger.w(
                TAG,
                "NPU requested but no supported NPU vendor detected. " +
                    "Falling back to GPU -> CPU.",
            )
        }
        // NPU delegate will be prepended when MediaPipe exposes Delegate.NPU
        // for supported vendors (Qualcomm QNN, MediaTek NeuroPilot).
        return listOf(Delegate.GPU, Delegate.CPU)
    }

    /**
     * Executes a factory function with automatic delegate fallback.
     *
     * Iterates through the fallback chain for the given [preference], calling [factory]
     * with each [Delegate]. Returns the first successful result. If all delegates fail,
     * rethrows the last exception.
     *
     * @param preference The user's inference delegate preference.
     * @param factory A function that creates the landmarker with the given delegate.
     *                Should throw [RuntimeException] if the delegate is unsupported.
     * @return The successfully created landmarker instance.
     * @throws RuntimeException if all delegates in the fallback chain fail.
     */
    fun <T> createWithFallback(
        preference: InferenceDelegate,
        factory: (Delegate) -> T,
    ): T {
        val chain = resolve(preference)
        var lastException: RuntimeException? = null

        for (delegate in chain) {
            try {
                val result = factory(delegate)
                if (delegate != chain.first()) {
                    FaceLinkLogger.w(TAG, "Using ${delegate.name} delegate (fallback from ${chain.first().name})")
                } else {
                    FaceLinkLogger.d(TAG, "Using ${delegate.name} delegate")
                }
                return result
            } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                FaceLinkLogger.w(TAG, "${delegate.name} delegate failed", e)
                lastException = e
            }
        }

        throw lastException ?: RuntimeException("No delegates available for $preference")
    }
}
