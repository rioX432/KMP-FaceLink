package io.github.kmpfacelink.live2d

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

/**
 * Drives a [Live2DRenderer] with avatar parameter emissions from this flow.
 *
 * Each emitted parameter map is forwarded to [Live2DRenderer.updateParameters].
 * This is a terminal flow operator that collects the flow.
 *
 * @param renderer the renderer to drive with parameter updates
 */
public suspend fun Flow<Map<String, Float>>.driveRenderer(renderer: Live2DRenderer) {
    onEach { parameters ->
        renderer.updateParameters(parameters)
    }.collect()
}
