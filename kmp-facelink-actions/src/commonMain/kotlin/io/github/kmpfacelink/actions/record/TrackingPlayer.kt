package io.github.kmpfacelink.actions.record

import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Plays back a recorded [TrackingSession] as a [Flow] of [FaceTrackingData].
 *
 * Frames are emitted with timing that matches the original recording intervals,
 * optionally scaled by [speed].
 *
 * @property session The recorded session to play back.
 * @property speed Playback speed multiplier. 1.0 = original speed, 2.0 = double speed.
 * @property loop Whether to loop playback continuously.
 */
public class TrackingPlayer(
    private val session: TrackingSession,
    private val speed: Float = DEFAULT_SPEED,
    private val loop: Boolean = false,
) {
    /**
     * Start playback, emitting frames as a Flow.
     *
     * The flow completes when all frames have been emitted (unless [loop] is true).
     * Cancel the flow collection to stop playback.
     */
    public fun play(): Flow<FaceTrackingData> = flow {
        if (session.isEmpty) return@flow

        do {
            val frames = session.frames
            emit(frames[0])

            for (i in 1 until frames.size) {
                val intervalMs = frames[i].timestampMs - frames[i - 1].timestampMs
                if (intervalMs > 0) {
                    delay((intervalMs / speed).toLong().coerceAtLeast(1L))
                }
                emit(frames[i])
            }
        } while (loop)
    }

    private companion object {
        const val DEFAULT_SPEED = 1f
    }
}

/**
 * Creates a playback [Flow] from this recorded session.
 *
 * @param speed Playback speed multiplier. Default is 1.0 (original speed).
 * @param loop Whether to loop playback continuously.
 */
public fun TrackingSession.play(
    speed: Float = 1f,
    loop: Boolean = false,
): Flow<FaceTrackingData> = TrackingPlayer(this, speed, loop).play()
