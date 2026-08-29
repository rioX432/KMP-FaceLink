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

    /** Value created by the fallback chain together with the delegate that actually succeeded. */
    internal data class Resolved<T>(
        val value: T,
        val delegate: Delegate,
    )

    /**
     * Returns an ordered fallback chain of [Delegate] values for the given [InferenceDelegate].
     *
     * - [InferenceDelegate.AUTO]: GPU -> CPU (conservative default; opt in to NPU explicitly)
     * - [InferenceDelegate.GPU]: GPU -> CPU
     * - [InferenceDelegate.NPU]: NPU -> GPU -> CPU (on supported devices, explicit opt-in)
     * - [InferenceDelegate.CPU]: CPU only
     */
    fun resolve(preference: InferenceDelegate): List<Delegate> {
        return when (preference) {
            InferenceDelegate.AUTO -> resolveAuto()
            InferenceDelegate.GPU -> listOf(Delegate.GPU, Delegate.CPU)
            InferenceDelegate.CPU -> listOf(Delegate.CPU)
            InferenceDelegate.NPU -> resolveNpu()
        }
    }

    /**
     * Resolves the AUTO delegate preference.
     *
     * Uses GPU -> CPU as the conservative default to avoid unexpected behavior
     * changes on NPU-equipped devices. Users who want NPU acceleration should
     * explicitly set [InferenceDelegate.NPU].
     */
    private fun resolveAuto(): List<Delegate> {
        return listOf(Delegate.GPU, Delegate.CPU)
    }

    /**
     * Resolves the NPU delegate fallback chain.
     *
     * Detects the device's NPU vendor and includes [Delegate.NPU] at the head of the
     * fallback chain for supported devices. MediaPipe Tasks Vision v0.10.32+ exposes
     * [Delegate.NPU] in its enum. The runtime will attempt NPU first, falling back to
     * GPU then CPU if the device lacks NPU support or the delegate fails at initialization.
     *
     * Note: Delegate.NPU availability in the enum does not guarantee runtime NPU acceleration
     * on all devices. The actual NPU execution depends on device hardware (Qualcomm QNN,
     * MediaTek NeuroPilot, Google Tensor) and LiteRT delegate availability.
     * Full NPU activation with vendor-specific configuration is tracked in #895.
     */
    private fun resolveNpu(): List<Delegate> {
        val vendor = NpuVendorDetector.detect()
        if (vendor != NpuVendor.NONE) {
            FaceLinkLogger.d(
                TAG,
                "NPU requested — $vendor NPU detected. " +
                    "Including Delegate.NPU in fallback chain: NPU -> GPU -> CPU.",
            )
            return listOf(Delegate.NPU, Delegate.GPU, Delegate.CPU)
        } else {
            FaceLinkLogger.w(
                TAG,
                "NPU requested but no supported NPU vendor detected. " +
                    "Falling back to GPU -> CPU.",
            )
            return listOf(Delegate.GPU, Delegate.CPU)
        }
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
    ): T = createResolvedWithFallback(preference, factory).value

    /**
     * Executes [factory] like [createWithFallback], retaining the actual successful delegate.
     * This is the provenance authority when AUTO or an explicit accelerator falls back.
     */
    fun <T> createResolvedWithFallback(
        preference: InferenceDelegate,
        factory: (Delegate) -> T,
    ): Resolved<T> {
        val chain = resolve(preference)
        var lastException: RuntimeException? = null

        for (delegate in chain) {
            try {
                val result = factory(delegate)
                logDelegateSuccess(delegate, chain)
                return Resolved(result, delegate)
            } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                logDelegateFailure(delegate, e)
                lastException = e
            }
        }

        throw lastException ?: RuntimeException("No delegates available for $preference")
    }

    private fun logDelegateSuccess(delegate: Delegate, chain: List<Delegate>) {
        if (delegate == Delegate.NPU) {
            val vendor = NpuVendorDetector.detect()
            FaceLinkLogger.d(TAG, "NPU delegate activated: vendor=$vendor")
        } else if (delegate != chain.first()) {
            FaceLinkLogger.w(
                TAG,
                "Using ${delegate.name} delegate (fallback from ${chain.first().name})",
            )
        } else {
            FaceLinkLogger.d(TAG, "Using ${delegate.name} delegate")
        }
    }

    private fun logDelegateFailure(delegate: Delegate, e: RuntimeException) {
        if (delegate == Delegate.NPU) {
            FaceLinkLogger.w(TAG, "NPU delegate failed, falling back to GPU", e)
        } else {
            FaceLinkLogger.w(TAG, "${delegate.name} delegate failed", e)
        }
    }
}
