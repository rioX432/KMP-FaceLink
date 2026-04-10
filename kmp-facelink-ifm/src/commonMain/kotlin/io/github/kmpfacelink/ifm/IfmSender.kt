package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.api.Releasable
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.networking.UdpSocket
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * iFacialMocap protocol sender.
 *
 * Sends face tracking data in iFacialMocap format over UDP,
 * compatible with VTube Studio, VSeeFace, and other receivers
 * that support the iFacialMocap protocol.
 *
 * Usage:
 * ```kotlin
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 * val sender = IfmSender(IfmConfig(host = "192.168.1.100"))
 * sender.start(faceTracker.trackingData, scope)
 * // ... later
 * sender.stop()
 * ```
 *
 * Tracking data is produced by [io.github.kmpfacelink.api.FaceTracker] in the core module.
 * UDP transport is provided by [io.github.kmpfacelink.networking.UdpSocket].
 *
 * @param config iFacialMocap connection configuration
 */
public class IfmSender(
    private val config: IfmConfig = IfmConfig(),
) : Releasable {
    private val socketLock = PlatformLock()
    private var socket = UdpSocket()
    private var collectJob: Job? = null

    /**
     * Starts collecting from a [FaceTrackingData] flow and sending iFacialMocap messages.
     *
     * The caller owns [scope] and is responsible for cancelling it
     * when sending is no longer needed.
     *
     * @param trackingFlow The face tracking data flow to consume
     * @param scope Coroutine scope for the collection job
     */
    public fun start(
        trackingFlow: Flow<FaceTrackingData>,
        scope: CoroutineScope,
    ) {
        stop()
        socketLock.withLock { socket = UdpSocket() }
        collectJob = scope.launch {
            trackingFlow.collect { data ->
                if (data.isTracking) {
                    sendFrame(data.blendShapes, data.headTransform, data.gazeData)
                }
            }
        }
    }

    /**
     * Sends a single frame of tracking data immediately.
     *
     * @param blendShapes Blend shape values to send
     * @param headTransform Head position and rotation to send
     * @param gazeData Eye gaze data for eye rotations. Null sends zeroes.
     */
    public fun sendFrame(
        blendShapes: BlendShapeData,
        headTransform: HeadTransform,
        gazeData: GazeData? = null,
    ) {
        val message = IfmMessageSerializer.serialize(
            blendShapes = blendShapes,
            headTransform = headTransform,
            gazeData = gazeData,
            dataVersion = config.dataVersion,
        )
        val bytes = message.encodeToByteArray()
        socketLock.withLock { socket.send(bytes, config.host, config.port) }
    }

    /**
     * Stops collecting tracking data and releases the UDP socket.
     */
    public fun stop() {
        socketLock.withLock { socket.close() }
        collectJob?.cancel()
        collectJob = null
    }

    /**
     * Releases all resources held by this sender.
     *
     * Stops any active collection and closes the UDP socket.
     * Safe to call multiple times.
     */
    public override fun release() {
        stop()
    }
}
