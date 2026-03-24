package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.networking.UdpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * val sender = IfmSender(IfmConfig(host = "192.168.1.100"))
 * sender.start(faceTracker.trackingData)
 * // ... later
 * sender.stop()
 * ```
 *
 * @param config iFacialMocap connection configuration
 */
public class IfmSender(
    private val config: IfmConfig = IfmConfig(),
) {
    private val socket = UdpSocket()
    private var collectJob: Job? = null

    /**
     * Starts collecting from a [FaceTrackingData] flow and sending iFacialMocap messages.
     *
     * @param trackingFlow The face tracking data flow to consume
     * @param scope Coroutine scope for the collection job
     */
    public fun start(
        trackingFlow: Flow<FaceTrackingData>,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    ) {
        stop()
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
        socket.send(bytes, config.host, config.port)
    }

    /**
     * Stops collecting tracking data and releases the UDP socket.
     */
    public fun stop() {
        collectJob?.cancel()
        collectJob = null
        socket.close()
    }
}
