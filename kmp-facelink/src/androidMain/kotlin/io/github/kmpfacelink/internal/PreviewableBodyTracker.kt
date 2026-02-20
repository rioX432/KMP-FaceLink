package io.github.kmpfacelink.internal

import androidx.camera.core.Preview

/**
 * Android-specific extension for [io.github.kmpfacelink.api.BodyTracker] that supports
 * camera preview. Call [setSurfaceProvider] before [io.github.kmpfacelink.api.BodyTracker.start]
 * to display the camera feed.
 */
public interface PreviewableBodyTracker {
    /**
     * Set a [Preview.SurfaceProvider] (typically from a PreviewView) to display the camera feed.
     * Must be called before [io.github.kmpfacelink.api.BodyTracker.start].
     */
    public fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider)
}
