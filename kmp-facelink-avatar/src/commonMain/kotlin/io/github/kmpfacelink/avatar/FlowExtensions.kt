package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Transforms a flow of [FaceTrackingData] into a flow of avatar parameters.
 *
 * Frames where [FaceTrackingData.isTracking] is `false` are filtered out.
 *
 * @param mapper the mapper to use for converting tracking data to avatar parameters
 * @return flow of parameter ID to value maps
 */
public fun Flow<FaceTrackingData>.toAvatarParameters(
    mapper: AvatarParameterMapper,
): Flow<Map<String, Float>> =
    filter { it.isTracking }
        .map { mapper.map(it) }
