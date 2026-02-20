package io.github.kmpfacelink.internal

import androidx.camera.core.Preview

/**
 * Android-specific extension for [io.github.kmpfacelink.api.HolisticTracker] that supports
 * camera preview. Call [setSurfaceProvider] before [io.github.kmpfacelink.api.HolisticTracker.start]
 * to display the camera feed.
 */
public interface PreviewableHolisticTracker {
    /**
     * Set a [Preview.SurfaceProvider] (typically from a PreviewView) to display the camera feed.
     * Must be called before [io.github.kmpfacelink.api.HolisticTracker.start].
     */
    public fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?)
}
