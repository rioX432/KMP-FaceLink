package io.github.kmpfacelink.internal.camera

import io.github.kmpfacelink.util.FaceLinkLogger
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.OpenCVFrameGrabber

/**
 * Manages webcam capture via JavaCV's OpenCVFrameGrabber.
 *
 * Provides a simple start/stop/grab lifecycle for camera frames.
 * Thread safety: methods must be called from a single thread (the tracking loop).
 */
internal class JavaCvCameraManager(
    private val deviceIndex: Int = 0,
    private val requestedWidth: Int = DEFAULT_WIDTH,
    private val requestedHeight: Int = DEFAULT_HEIGHT,
) {
    private var grabber: OpenCVFrameGrabber? = null

    val isStarted: Boolean get() = grabber != null

    val frameWidth: Int get() = grabber?.imageWidth ?: 0
    val frameHeight: Int get() = grabber?.imageHeight ?: 0

    fun start() {
        if (grabber != null) return
        val g = OpenCVFrameGrabber(deviceIndex)
        g.imageWidth = requestedWidth
        g.imageHeight = requestedHeight
        g.start()
        grabber = g
        FaceLinkLogger.d(TAG, "Camera started: ${g.imageWidth}x${g.imageHeight}")
    }

    fun grab(): Frame? = grabber?.grab()

    fun stop() {
        grabber?.let { g ->
            try {
                g.stop()
                g.release()
            } catch (expected: Exception) {
                FaceLinkLogger.w(TAG, "Error stopping camera: ${expected.message}")
            }
            grabber = null
        }
    }

    companion object {
        private const val TAG = "JavaCvCameraManager"
        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
    }
}
