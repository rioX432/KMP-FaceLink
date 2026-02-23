package io.github.kmpfacelink.rive

import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Transforms a flow of [FaceTrackingData] into a flow of Rive State Machine inputs.
 *
 * Frames where [FaceTrackingData.isTracking] is `false` are filtered out.
 *
 * @param mapper the mapper to use for converting tracking data to Rive inputs
 * @return flow of input name to [RiveInput] maps
 */
public fun Flow<FaceTrackingData>.toRiveInputs(
    mapper: RiveParameterMapper,
): Flow<Map<String, RiveInput>> =
    filter { it.isTracking }
        .map { mapper.map(it) }

/**
 * Collects a flow of Rive inputs and applies them to the given [renderer].
 *
 * This is a terminal operator that suspends until the upstream flow completes or is cancelled.
 *
 * @param renderer the renderer to drive with the collected inputs
 */
public suspend fun Flow<Map<String, RiveInput>>.driveRiveRenderer(
    renderer: RiveRenderer,
) {
    onEach { inputs ->
        renderer.updateInputs(inputs)
    }.collect {}
}
