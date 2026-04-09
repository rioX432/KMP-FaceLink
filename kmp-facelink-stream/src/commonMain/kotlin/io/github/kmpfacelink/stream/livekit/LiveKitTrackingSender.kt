package io.github.kmpfacelink.stream.livekit

import io.github.kmpfacelink.api.Releasable
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.stream.buildStreamFrame
import io.github.kmpfacelink.util.FaceLinkLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * High-level sender that bridges tracking flows to a LiveKit room.
 *
 * Collects from face and/or hand tracking flows, rate-limits to the
 * configured FPS, and publishes frames via [LiveKitDataChannelPublisher].
 *
 * Usage:
 * ```kotlin
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 * val config = LiveKitBridgeConfig(
 *     serverUrl = "wss://livekit.example.com",
 *     token = "jwt-token",
 *     roomName = "avatar-room",
 * )
 * val sender = LiveKitTrackingSender(config)
 * sender.start(faceTracker.trackingData, handTracker.trackingData, scope)
 * // ... later
 * sender.stop()
 * ```
 *
 * @param config Bridge configuration (server URL, token, room, FPS, etc.)
 * @param publisher Optional publisher instance. If null, a default
 *        [LiveKitDataChannelPublisher] is created from [config].
 */
@OptIn(ExperimentalAtomicApi::class)
public class LiveKitTrackingSender(
    private val config: LiveKitBridgeConfig,
    private val publisher: LiveKitDataChannelPublisher =
        LiveKitDataChannelPublisher(config),
) : Releasable {

    private val released = AtomicBoolean(false)
    private val mutex = Mutex()
    private var faceJob: Job? = null
    private var handJob: Job? = null
    private var publishJob: Job? = null
    private var latestFaceData: FaceTrackingData? = null
    private var latestHandData: HandTrackingData? = null

    /** Observable connection state of the underlying publisher. */
    public val connectionState: StateFlow<LiveKitConnectionState>
        get() = publisher.liveKitConnectionState

    /**
     * Connects to the LiveKit room and starts collecting from tracking
     * flows, publishing at the configured rate.
     *
     * The caller owns [scope] and is responsible for cancelling it
     * when streaming is no longer needed.
     *
     * @param faceFlow Face tracking data flow, or null to skip face data
     * @param handFlow Hand tracking data flow, or null to skip hand data
     * @param scope Coroutine scope for collection and publish jobs
     */
    public suspend fun start(
        faceFlow: Flow<FaceTrackingData>? = null,
        handFlow: Flow<HandTrackingData>? = null,
        scope: CoroutineScope,
    ) {
        stop()
        publisher.connect(scope)
        faceJob = faceFlow?.let { flow ->
            scope.launch { flow.collect { mutex.withLock { latestFaceData = it } } }
        }
        handJob = handFlow?.let { flow ->
            scope.launch { flow.collect { mutex.withLock { latestHandData = it } } }
        }
        publishJob = scope.launch { publishLoop() }
    }

    /**
     * Stops collecting, publishing, and disconnects from the LiveKit room.
     */
    public suspend fun stop() {
        faceJob?.cancel()
        handJob?.cancel()
        publishJob?.cancel()
        faceJob = null
        handJob = null
        publishJob = null
        latestFaceData = null
        latestHandData = null
        publisher.disconnect()
    }

    /**
     * Releases all resources held by this sender.
     *
     * Uses atomic CAS to ensure cleanup runs exactly once. Stops any
     * active collection and publishing, and releases the underlying
     * publisher. Safe to call multiple times and from any thread.
     *
     * Note: this method blocks the calling thread because [Releasable]
     * is a synchronous interface. Prefer [stop] from a coroutine.
     */
    public override fun release() {
        if (!released.compareAndSet(expectedValue = false, newValue = true)) return
        faceJob?.cancel()
        handJob?.cancel()
        publishJob?.cancel()
        faceJob = null
        handJob = null
        publishJob = null
        latestFaceData = null
        latestHandData = null
        publisher.release()
    }

    private suspend fun CoroutineScope.publishLoop() {
        val frameIntervalMs = MS_PER_SEC / config.publishRateHz
        while (isActive) {
            publishLatestFrame()
            delay(frameIntervalMs)
        }
    }

    private suspend fun publishLatestFrame() {
        try {
            val (face, hand) = mutex.withLock {
                latestFaceData to latestHandData
            }
            if (face != null || hand != null) {
                val timestampMs = face?.timestampMs
                    ?: hand?.timestampMs ?: 0L
                val frame = buildStreamFrame(face, hand, timestampMs)
                publisher.publish(frame)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            FaceLinkLogger.w(TAG, "Publish error: ${e.message}", e)
        }
    }

    private companion object {
        private const val TAG = "LiveKitTrackingSender"
        private const val MS_PER_SEC = 1000L
    }
}
