package io.github.kmpfacelink.internal

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.os.Build
import io.github.kmpfacelink.model.NpuVendor
import io.github.kmpfacelink.util.FaceLinkLogger

private const val TAG = "HardwareCapabilityDetector"
private const val GLES_MAJOR_VERSION_FOR_COMPUTE = 3
private const val GLES_MINOR_VERSION_FOR_COMPUTE = 1

// EGL 1.5 spec §3.7.1 — context creation attributes
private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098

// EGL 1.5 spec §3.5.3 — pbuffer surface size attributes
private const val EGL_SURFACE_WIDTH = 0x3057
private const val EGL_SURFACE_HEIGHT = 0x3058
private const val EGL_PBUFFER_SIZE = 1

/**
 * Detects hardware capabilities relevant to ML inference acceleration on Android.
 *
 * This detector checks for GPU compute shader support (OpenGL ES 3.1+),
 * NPU vendor availability, and NNAPI availability, which are prerequisites
 * for MediaPipe GPU, NPU, and NNAPI delegates.
 */
internal object HardwareCapabilityDetector {

    /**
     * Result of hardware capability detection.
     *
     * @property gpuComputeSupported Whether the device supports OpenGL ES 3.1+ compute shaders,
     *           required by MediaPipe's GPU delegate.
     * @property npuVendor The detected NPU vendor (Qualcomm QNN, MediaTek NeuroPilot, or none).
     * @property nnapiAvailable Whether the Android NNAPI is available on this device.
     *           Deprecated: NNAPI is deprecated starting in Android 15 (API 35).
     * @property androidApiLevel The device's Android API level.
     */
    data class Capabilities(
        val gpuComputeSupported: Boolean,
        val npuVendor: NpuVendor,
        @Deprecated("NNAPI is deprecated in Android 15 (API 35)")
        val nnapiAvailable: Boolean,
        val androidApiLevel: Int,
    ) {
        /**
         * Whether a supported NPU vendor (Qualcomm QNN or MediaTek NeuroPilot) was detected.
         *
         * Note: This indicates hardware capability only. The MediaPipe NPU delegate is not
         * yet functional, so NPU inference currently falls back to GPU -> CPU.
         */
        val hasNpuCapability: Boolean get() = npuVendor != NpuVendor.NONE
    }

    /**
     * Detects hardware capabilities for the current device.
     *
     * GPU detection creates a temporary EGL context to query the OpenGL ES version.
     * This is a relatively expensive operation — callers should cache the result.
     */
    fun detect(@Suppress("UNUSED_PARAMETER") context: Context): Capabilities {
        val gpuSupported = checkGpuComputeSupport()
        val npuVendor = NpuVendorDetector.detect()
        val nnapiAvailable = checkNnapiAvailability()

        FaceLinkLogger.d(
            TAG,
            "Capabilities: GPU=$gpuSupported, NPU=$npuVendor, " +
                "NNAPI=$nnapiAvailable, API=${Build.VERSION.SDK_INT}",
        )

        return Capabilities(
            gpuComputeSupported = gpuSupported,
            npuVendor = npuVendor,
            nnapiAvailable = nnapiAvailable,
            androidApiLevel = Build.VERSION.SDK_INT,
        )
    }

    /**
     * Checks whether the GPU supports OpenGL ES 3.1+ compute shaders.
     *
     * Creates a temporary EGL context, queries the GL version string, and tears down.
     * Returns false on any failure (no GPU, broken driver, etc.).
     */
    private fun checkGpuComputeSupport(): Boolean {
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE

        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val initialized = display != EGL14.EGL_NO_DISPLAY && initializeEgl(display)
            if (!initialized) return false

            val config = chooseEglConfig(display) ?: return false
            eglContext = createEglContext(display, config)
            surface = createEglSurface(display, config)
            val ready = eglContext != EGL14.EGL_NO_CONTEXT &&
                surface != EGL14.EGL_NO_SURFACE &&
                EGL14.eglMakeCurrent(display, surface, surface, eglContext)
            if (!ready) return false

            val glVersion = GLES30.glGetString(GLES30.GL_VERSION)
            glVersion != null && parseGlesVersion(glVersion)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            FaceLinkLogger.w(TAG, "GPU capability check failed", e)
            false
        } finally {
            cleanupEgl(display, eglContext, surface)
        }
    }

    private fun initializeEgl(display: EGLDisplay): Boolean {
        val version = IntArray(2)
        return EGL14.eglInitialize(display, version, 0, version, 1)
    }

    private fun chooseEglConfig(display: EGLDisplay): EGLConfig? {
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE,
            EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val success = EGL14.eglChooseConfig(
            display,
            configAttribs,
            0,
            configs,
            0,
            1,
            numConfigs,
            0,
        )
        if (!success || numConfigs[0] == 0) return null
        return configs[0]
    }

    private fun createEglContext(display: EGLDisplay, config: EGLConfig): EGLContext {
        val contextAttribs = intArrayOf(
            EGL_CONTEXT_CLIENT_VERSION,
            GLES_MAJOR_VERSION_FOR_COMPUTE,
            EGL14.EGL_NONE,
        )
        return EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            contextAttribs,
            0,
        )
    }

    private fun createEglSurface(display: EGLDisplay, config: EGLConfig): EGLSurface {
        val surfaceAttribs = intArrayOf(
            EGL_SURFACE_WIDTH,
            EGL_PBUFFER_SIZE,
            EGL_SURFACE_HEIGHT,
            EGL_PBUFFER_SIZE,
            EGL14.EGL_NONE,
        )
        return EGL14.eglCreatePbufferSurface(
            display,
            config,
            surfaceAttribs,
            0,
        )
    }

    /** Parses "OpenGL ES X.Y ..." and returns true if version >= 3.1. */
    internal fun parseGlesVersion(versionString: String): Boolean {
        val match = Regex("""OpenGL ES (\d+)\.(\d+)""").find(versionString) ?: return false
        val major = match.groupValues[1].toIntOrNull() ?: return false
        val minor = match.groupValues[2].toIntOrNull() ?: return false
        return major > GLES_MAJOR_VERSION_FOR_COMPUTE ||
            (major == GLES_MAJOR_VERSION_FOR_COMPUTE && minor >= GLES_MINOR_VERSION_FOR_COMPUTE)
    }

    private fun cleanupEgl(display: EGLDisplay, context: EGLContext, surface: EGLSurface) {
        if (display == EGL14.EGL_NO_DISPLAY) return
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
        )
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        EGL14.eglTerminate(display)
    }

    /** NNAPI is available on Android 8.1 (API 27) and later. */
    private fun checkNnapiAvailability(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
    }
}
