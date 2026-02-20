@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.CompositeHolisticTracker
import platform.ARKit.ARSession
import platform.AVFoundation.AVCaptureSession

/**
 * Returns the underlying [ARSession] for camera preview, or null if face tracking is not enabled.
 * Only returns a non-null value after [HolisticTracker.start] has been called.
 */
public fun HolisticTracker.getARSession(): ARSession? =
    (this as? CompositeHolisticTracker)?.arSession

/**
 * Returns the underlying [AVCaptureSession] for camera preview, or null if hand/body
 * tracking is not enabled. Only returns a non-null value after [HolisticTracker.start]
 * has been called.
 */
public fun HolisticTracker.getCaptureSession(): AVCaptureSession? =
    (this as? CompositeHolisticTracker)?.visionCaptureSession
