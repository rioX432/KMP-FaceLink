package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.vmc.internal.UdpSocket
import io.github.kmpfacelink.vmc.osc.OscEncoder
import io.github.kmpfacelink.vmc.osc.OscMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * VMC Protocol sender (Performer role).
 *
 * Sends face tracking data as VMC Protocol messages over UDP.
 * Converts KMP-FaceLink's [BlendShapeData] to `/VMC/Ext/Blend/Val` messages
 * and [HeadTransform] to `/VMC/Ext/Bone/Pos` messages.
 *
 * Usage:
 * ```kotlin
 * val sender = VmcSender(VmcConfig(host = "192.168.1.100"))
 * sender.start(faceTracker.trackingData)
 * // ... later
 * sender.stop()
 * ```
 *
 * @param config VMC connection configuration
 */
public class VmcSender(
    private val config: VmcConfig = VmcConfig(),
) {
    private val socket = UdpSocket()
    private var collectJob: Job? = null
    private var startTimeMs: Long = 0L

    /**
     * Starts collecting from a [FaceTrackingData] flow and sending VMC messages.
     *
     * @param trackingFlow The face tracking data flow to consume
     * @param scope Coroutine scope for the collection job
     */
    public fun start(
        trackingFlow: Flow<FaceTrackingData>,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    ) {
        stop()
        startTimeMs = currentTimeMs()
        collectJob = scope.launch {
            trackingFlow.collect { data ->
                if (data.isTracking) {
                    sendFrame(data.blendShapes, data.headTransform, data.timestampMs)
                }
            }
        }
    }

    /**
     * Sends a single frame of tracking data immediately.
     *
     * @param blendShapes Blend shape values to send
     * @param headTransform Head position and rotation to send
     * @param timestampMs Frame timestamp in milliseconds
     */
    public fun sendFrame(blendShapes: BlendShapeData, headTransform: HeadTransform, timestampMs: Long) {
        val messages = buildFrameMessages(blendShapes, headTransform, timestampMs)
        sendMessages(messages)
    }

    /**
     * Stops collecting tracking data and releases the UDP socket.
     */
    public fun stop() {
        collectJob?.cancel()
        collectJob = null
        socket.close()
    }

    private fun buildFrameMessages(
        blendShapes: BlendShapeData,
        headTransform: HeadTransform,
        timestampMs: Long,
    ): List<OscMessage> {
        val relativeTimeSec = (timestampMs - startTimeMs).toFloat() / MS_PER_SEC
        val messages = mutableListOf<OscMessage>()
        messages.add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
        messages.addAll(VmcMessageBuilder.buildBlendShapeMessages(blendShapes))
        messages.add(VmcMessageBuilder.buildHeadBoneMessage(headTransform))
        messages.add(VmcMessageBuilder.buildTimestampMessage(relativeTimeSec))
        return messages
    }

    private fun sendMessages(messages: List<OscMessage>) {
        if (config.sendBundles) {
            val bundleBytes = OscEncoder.encodeBundle(messages)
            socket.send(bundleBytes, config.host, config.port)
        } else {
            for (message in messages) {
                val bytes = OscEncoder.encode(message)
                socket.send(bytes, config.host, config.port)
            }
        }
    }
}

private const val MS_PER_SEC = 1000f

internal expect fun currentTimeMs(): Long
