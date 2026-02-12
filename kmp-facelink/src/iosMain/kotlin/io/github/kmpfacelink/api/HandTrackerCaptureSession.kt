package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.VisionHandTracker
import platform.AVFoundation.AVCaptureSession

/**
 * Returns the underlying [AVCaptureSession] for camera preview, or null if not available.
 * Only returns a non-null value after [HandTracker.start] has been called.
 */
public fun HandTracker.getCaptureSession(): AVCaptureSession? =
    (this as? VisionHandTracker)?.captureSession
