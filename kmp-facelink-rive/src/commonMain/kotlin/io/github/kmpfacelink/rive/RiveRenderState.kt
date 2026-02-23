package io.github.kmpfacelink.rive

/**
 * Lifecycle states for a [RiveRenderer].
 */
public enum class RiveRenderState {
    /** Renderer has been created but no model is loaded. */
    UNINITIALIZED,

    /** A model is currently being loaded. */
    LOADING,

    /** Model is loaded and ready to receive inputs. */
    READY,

    /** An error occurred during loading or rendering. */
    ERROR,

    /** Renderer has been released and cannot be reused. */
    RELEASED,
}
