package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.VisionHandTracker
import platform.AVFoundation.AVCaptureSession

/**
 * Returns the underlying [AVCaptureSession] for camera preview, or null if not available.
 * Only returns a non-null value after [HandTracker.start] has been called.
 *
 * This extension casts the public [HandTracker] interface to the internal
 * [VisionHandTracker] implementation. It requires the tracker to be created
 * via the standard factory; custom implementations will return null.
 */
public fun HandTracker.getCaptureSession(): AVCaptureSession? =
    (this as? VisionHandTracker)?.captureSession
