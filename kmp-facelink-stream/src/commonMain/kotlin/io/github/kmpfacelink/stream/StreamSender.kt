package io.github.kmpfacelink.stream

import io.github.kmpfacelink.api.Releasable
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * High-level sender that bridges tracking flows to [StreamServer].
 *
 * Collects from face and/or hand tracking flows, rate-limits to the
 * configured FPS, and broadcasts frames to all connected WebSocket clients.
 *
 * Usage:
 * ```kotlin
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 * val server = StreamServer(config)
 * val sender = StreamSender(server, config)
 * server.start(scope)
 * sender.start(faceTracker.trackingData, handTracker.trackingData, scope)
 * // ... later
 * sender.stop()
 * server.stop()
 * ```
 *
 * @param server The [StreamServer] to send frames through
 * @param config Streaming configuration (FPS, format)
 */
public class StreamSender(
    private val server: StreamServer,
    private val config: StreamConfig = StreamConfig(),
) : Releasable {
    private val mutex = Mutex()
    private var faceJob: Job? = null
    private var handJob: Job? = null
    private var broadcastJob: Job? = null
    private var latestFaceData: FaceTrackingData? = null
    private var latestHandData: HandTrackingData? = null

    /**
     * Starts collecting from tracking flows and streaming at the configured FPS.
     *
     * The caller owns [scope] and is responsible for cancelling it
     * when streaming is no longer needed.
     *
     * @param faceFlow Face tracking data flow, or null to skip face data
     * @param handFlow Hand tracking data flow, or null to skip hand data
     * @param scope Coroutine scope for collection jobs
     */
    public fun start(
        faceFlow: Flow<FaceTrackingData>? = null,
        handFlow: Flow<HandTrackingData>? = null,
        scope: CoroutineScope,
    ) {
        stop()
        faceJob = faceFlow?.let { flow ->
            scope.launch { flow.collect { mutex.withLock { latestFaceData = it } } }
        }
        handJob = handFlow?.let { flow ->
            scope.launch { flow.collect { mutex.withLock { latestHandData = it } } }
        }
        broadcastJob = scope.launch { broadcastLoop() }
    }

    /**
     * Stops collecting and broadcasting.
     */
    public fun stop() {
        faceJob?.cancel()
        handJob?.cancel()
        broadcastJob?.cancel()
        faceJob = null
        handJob = null
        broadcastJob = null
        latestFaceData = null
        latestHandData = null
    }

    /**
     * Releases all resources held by this sender.
     *
     * Stops any active collection and broadcasting.
     * Safe to call multiple times.
     */
    public override fun release() {
        stop()
    }

    private suspend fun broadcastLoop() {
        val frameIntervalMs = MS_PER_SEC / config.targetFps
        while (true) {
            val (face, hand) = mutex.withLock { latestFaceData to latestHandData }
            if (face != null || hand != null) {
                val timestampMs = face?.timestampMs ?: hand?.timestampMs ?: 0L
                val frame = buildStreamFrame(face, hand, timestampMs)
                server.sendFrame(frame)
            }
            delay(frameIntervalMs)
        }
    }
}

private const val MS_PER_SEC = 1000L
