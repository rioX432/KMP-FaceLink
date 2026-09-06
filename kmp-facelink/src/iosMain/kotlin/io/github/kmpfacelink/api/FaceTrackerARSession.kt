@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.ARKitFaceTracker
import platform.ARKit.ARSession

/**
 * Returns the underlying [ARSession] for camera preview, or null if not available.
 * Only returns a non-null value after [FaceTracker.start] has been called.
 *
 * Use this to share the tracker's session with an `ARSCNView` instead of creating
 * a second session that would compete for the front camera.
 *
 * This extension casts the public [FaceTracker] interface to the internal
 * [ARKitFaceTracker] implementation. It requires the tracker to be created
 * via the standard factory; custom implementations will return null.
 */
public fun FaceTracker.getARSession(): ARSession? =
    (this as? ARKitFaceTracker)?.arSession
