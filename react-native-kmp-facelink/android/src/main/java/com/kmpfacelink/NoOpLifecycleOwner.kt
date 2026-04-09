package com.kmpfacelink

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A [LifecycleOwner] that reports [Lifecycle.State.RESUMED] immediately.
 *
 * React Native manages its own activity lifecycle, so we provide a lifecycle
 * owner that stays in RESUMED state for CameraX binding.
 */
internal class NoOpLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = registry

    internal fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
