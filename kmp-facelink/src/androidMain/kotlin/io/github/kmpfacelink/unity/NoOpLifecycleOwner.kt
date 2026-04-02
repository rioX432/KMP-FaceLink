package io.github.kmpfacelink.unity

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A [LifecycleOwner] that reports [Lifecycle.State.RESUMED] immediately.
 *
 * Unity manages its own activity lifecycle, so we provide a lifecycle owner
 * that stays in RESUMED state for CameraX binding. Unity's pause/resume
 * is handled by [FaceLinkUnityFacade.stopTracking] / [FaceLinkUnityFacade.startTracking].
 */
internal class NoOpLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = registry

    /** Transition to DESTROYED state so CameraX unbinds and releases resources. */
    internal fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
