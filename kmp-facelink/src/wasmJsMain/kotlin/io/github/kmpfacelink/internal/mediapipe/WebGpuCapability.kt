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
 * Whether the browser supports the WebNN API for NPU/GPU/CPU neural network inference.
 *
 * WebNN (Web Neural Network API) is a W3C standard that provides access to hardware
 * accelerators including NPUs. As of April 2026, only Chromium-based browsers (Chrome 146+,
 * Edge 146+) ship stable support. Firefox and Safari have not yet implemented it.
 *
 * This enum represents the best available WebNN device type, which is separate from
 * [GpuBackend] — WebNN targets dedicated ML accelerators while WebGPU targets general
 * GPU compute.
 *
 * @see <a href="https://www.w3.org/TR/webnn/">W3C WebNN Specification</a>
 */
internal enum class WebNnDeviceType {
    /** NPU (Neural Processing Unit) is available for ML inference. */
    NPU,

    /** WebNN GPU device is available (distinct from WebGPU — uses ML-optimized paths). */
    GPU,

    /** WebNN CPU device is available. */
    CPU,

    /** WebNN API is not available in this browser. */
    UNAVAILABLE,
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

    /**
     * Probes the browser for WebNN API availability and returns the best device type.
     *
     * WebNN can target NPU, GPU, or CPU backends for ML inference. This method checks
     * API presence only — it does not create an ML context or run inference.
     *
     * As of April 2026, WebNN is only available in Chromium 146+. This detection is
     * provided as a scaffold for future integration when broader browser support lands.
     *
     * @return the best available [WebNnDeviceType], or [WebNnDeviceType.UNAVAILABLE]
     */
    fun detectWebNn(): WebNnDeviceType {
        if (!isWebNnAvailable()) {
            FaceLinkLogger.d(TAG, "WebNN API not available in this browser")
            return WebNnDeviceType.UNAVAILABLE
        }
        FaceLinkLogger.d(TAG, "WebNN API detected — NPU/GPU/CPU inference possible")
        // TODO: When WebNN is production-ready, probe for specific device types
        //  by attempting MLContext creation with deviceType: "npu", "gpu", "cpu".
        //  For now, just report API presence without probing device availability.
        return WebNnDeviceType.CPU
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

/**
 * Checks whether the WebNN API is present (`navigator.ml` exists).
 *
 * This only checks for API presence, not whether a specific device type (NPU/GPU)
 * is available. Device probing requires creating an MLContext which is deferred
 * until WebNN reaches broader browser support.
 */
private fun isWebNnAvailable(): Boolean =
    js("(typeof navigator !== 'undefined' && navigator.ml != null)")
