package io.github.kmpfacelink.stream

import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val MILLIS_PER_SECOND = 1000L

/**
 * Terminal operator that converts [FaceTrackingData] to parameters and sends them
 * to the given [StreamClient].
 *
 * This is a suspending function that collects the flow indefinitely.
 *
 * @param client The stream client to send parameters to
 */
public suspend fun Flow<FaceTrackingData>.streamTo(client: StreamClient) {
    collect { data ->
        val params = ParameterConverter.convert(data)
        client.sendParameters(params, data.isTracking)
    }
}

/**
 * Terminal operator that sends pre-mapped parameters to the given [StreamClient].
 *
 * @param client The stream client to send parameters to
 * @param faceFound Whether a face is currently being tracked
 */
public suspend fun Flow<Map<String, Float>>.streamTo(
    client: StreamClient,
    faceFound: Boolean = true,
) {
    collect { params ->
        client.sendParameters(params, faceFound)
    }
}

/**
 * Composable operator that limits emission rate to [maxFps] frames per second.
 *
 * Frames arriving faster than the target rate are dropped.
 *
 * @param maxFps Maximum frames per second (must be positive)
 * @return A new flow with rate-limited emissions
 */
public fun <T> Flow<T>.rateLimit(maxFps: Int): Flow<T> {
    require(maxFps > 0) { "maxFps must be positive, was $maxFps" }
    val intervalMs = MILLIS_PER_SECOND / maxFps
    return flow {
        var lastEmitTime = 0L
        collect { value ->
            val now = currentTimeMillis()
            if (now - lastEmitTime >= intervalMs) {
                lastEmitTime = now
                emit(value)
            }
        }
    }
}

internal expect fun currentTimeMillis(): Long
