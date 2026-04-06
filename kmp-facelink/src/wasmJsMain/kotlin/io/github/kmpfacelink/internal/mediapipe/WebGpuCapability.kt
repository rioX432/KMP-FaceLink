package io.github.kmpfacelink.internal.mediapipe

import io.github.kmpfacelink.util.FaceLinkLogger
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * Detected GPU backend available in the current browser environment.
 */
internal enum class GpuBackend {
    /** WebGPU is available (Chrome 113+, Safari 26+, Firefox 141+ on Windows). */
    WEBGPU,

    /** WebGL 2.0 is available as a fallback GPU backend. */
    WEBGL,

    /** No GPU backend detected; CPU-only inference. */
    NONE,
}

/**
 * Detects the best available GPU backend in the current browser.
 *
 * Checks for WebGPU first (`navigator.gpu.requestAdapter()`), then falls back
 * to WebGL 2.0 (`OffscreenCanvas.getContext("webgl2")`), and finally [GpuBackend.NONE].
 */
internal object WebGpuCapability {

    /**
     * Probes the browser for GPU capabilities and returns the best available backend.
     *
     * This is a suspend function because WebGPU adapter request is asynchronous.
     */
    suspend fun detect(): GpuBackend {
        if (isWebGpuAvailable()) {
            val adapterAvailable = tryRequestWebGpuAdapter()
            if (adapterAvailable) {
                FaceLinkLogger.d(TAG, "WebGPU adapter available")
                return GpuBackend.WEBGPU
            }
            FaceLinkLogger.d(TAG, "WebGPU API present but no adapter available")
        }

        if (isWebGl2Available()) {
            FaceLinkLogger.d(TAG, "Falling back to WebGL 2.0")
            return GpuBackend.WEBGL
        }

        FaceLinkLogger.w(TAG, "No GPU backend detected, using CPU")
        return GpuBackend.NONE
    }

    /**
     * Resolves the MediaPipe delegate string for the detected backend.
     *
     * MediaPipe Tasks Vision JS accepts "GPU" or "CPU" as the delegate value.
     * When the backend is [GpuBackend.WEBGPU] or [GpuBackend.WEBGL], we request "GPU"
     * and let MediaPipe select the best available GPU path internally.
     */
    fun resolveDelegate(backend: GpuBackend): String = when (backend) {
        GpuBackend.WEBGPU, GpuBackend.WEBGL -> DELEGATE_GPU
        GpuBackend.NONE -> DELEGATE_CPU
    }

    private suspend fun tryRequestWebGpuAdapter(): Boolean =
        try {
            val adapter = awaitJsPromise<JsAny?>(requestWebGpuAdapterJs())
            adapter != null
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            FaceLinkLogger.w(TAG, "WebGPU adapter request failed", e)
            false
        }

    private const val TAG = "WebGpuCapability"
    private const val DELEGATE_GPU = "GPU"
    private const val DELEGATE_CPU = "CPU"
}

/**
 * Checks whether the WebGPU API is present (`navigator.gpu` exists).
 */
private fun isWebGpuAvailable(): Boolean =
    js("(typeof navigator !== 'undefined' && navigator.gpu != null)")

/**
 * Requests a WebGPU adapter from the browser.
 *
 * @return Promise resolving to a GPUAdapter or null
 */
private fun requestWebGpuAdapterJs(): Promise<JsAny?> =
    js("navigator.gpu.requestAdapter()")

/**
 * Checks whether WebGL 2.0 is available via OffscreenCanvas.
 */
private fun isWebGl2Available(): Boolean =
    js("(typeof OffscreenCanvas !== 'undefined' && new OffscreenCanvas(1, 1).getContext('webgl2') != null)")
