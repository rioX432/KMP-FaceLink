// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

package io.github.kmpfacelink.cbridge

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A [LifecycleOwner] that reports [Lifecycle.State.RESUMED] immediately.
 *
 * Non-KMP consumers (Unity, React Native, Flutter) manage their own lifecycle,
 * so we provide a lifecycle owner that stays in RESUMED state for CameraX binding.
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
