package io.github.kmpfacelink.live2d

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic interface for rendering a Live2D Cubism model.
 *
 * Implementations wrap the platform-specific Cubism SDK (Java on Android, Native on iOS)
 * and are responsible for model loading, parameter updates, and frame rendering.
 *
 * Typical usage:
 * ```
 * renderer.initialize(modelInfo)
 * trackingDataFlow
 *     .toAvatarParameters(mapper)
 *     .driveRenderer(renderer)
 * ```
 */
@ExperimentalFaceLinkApi
public interface Live2DRenderer {
    /** Current lifecycle state of this renderer. */
    public val state: StateFlow<Live2DRenderState>

    /** Model info for the currently loaded model, or null if not initialized. */
    public val modelInfo: Live2DModelInfo?

    /**
     * Loads a Live2D model and prepares the renderer for drawing.
     *
     * After successful initialization, [state] transitions to [Live2DRenderState.READY].
     *
     * @param modelInfo metadata describing the model to load
     * @throws IllegalStateException if already initialized or released
     */
    public suspend fun initialize(modelInfo: Live2DModelInfo)

    /**
     * Updates the model's parameters for the next frame.
     *
     * This is called from the tracking pipeline on each frame emission.
     * Parameters are stored and applied on the next render cycle.
     *
     * @param parameters map of Live2D parameter ID to float value
     */
    public fun updateParameters(parameters: Map<String, Float>)

    /**
     * Releases all resources held by this renderer.
     *
     * After calling this, [state] transitions to [Live2DRenderState.RELEASED]
     * and the renderer cannot be reused.
     */
    public fun release()
}
