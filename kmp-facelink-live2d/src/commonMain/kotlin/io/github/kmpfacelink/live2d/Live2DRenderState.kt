package io.github.kmpfacelink.live2d

import io.github.kmpfacelink.ExperimentalFaceLinkApi

/**
 * Represents the lifecycle state of a [Live2DRenderer].
 */
@ExperimentalFaceLinkApi
public enum class Live2DRenderState {
    /** Renderer has not been initialized yet. */
    UNINITIALIZED,

    /** Model is loaded and renderer is ready to receive parameters. */
    READY,

    /** Renderer is actively rendering frames. */
    RENDERING,

    /** An error occurred during initialization or rendering. */
    ERROR,

    /** Renderer resources have been released. */
    RELEASED,
}
