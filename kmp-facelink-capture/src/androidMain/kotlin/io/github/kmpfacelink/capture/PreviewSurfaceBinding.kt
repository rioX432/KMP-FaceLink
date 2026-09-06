package io.github.kmpfacelink.capture

import androidx.camera.core.Preview

/**
 * Owns only a [Preview] surface-provider reference. It deliberately has no camera-provider or
 * analysis dependency, so a Compose attach/detach cannot cause a camera rebind.
 */
internal class PreviewSurfaceBinding {
    var current: Preview.SurfaceProvider? = null
        private set

    fun attach(
        provider: Preview.SurfaceProvider,
        updateSurfaceProvider: (Preview.SurfaceProvider?) -> Unit,
    ) {
        if (current === provider) return
        current = provider
        updateSurfaceProvider(provider)
    }

    fun detach(
        provider: Preview.SurfaceProvider,
        updateSurfaceProvider: (Preview.SurfaceProvider?) -> Unit,
    ) {
        if (current !== provider) return
        current = null
        updateSurfaceProvider(null)
    }

    fun clear() {
        current = null
    }
}
