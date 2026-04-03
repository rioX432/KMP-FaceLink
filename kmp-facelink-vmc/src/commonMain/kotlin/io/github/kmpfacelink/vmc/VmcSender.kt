package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.api.Releasable
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.networking.UdpSocket
import io.github.kmpfacelink.networking.currentTimeMs
import io.github.kmpfacelink.vmc.osc.OscEncoder
import io.github.kmpfacelink.vmc.osc.OscMessage
import kotlinx.coroutines.CoroutineScope
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
 * Head rotation uses the [VmcBoneMapping.HEAD] bone name following the VRM 1.0
 * humanoid specification. Euler angles are converted to Unity-compatible quaternions
 * (Y*X*Z rotation order).
 *
 * ## Phone-as-Tracker Example
 *
 * Use a mobile device as a wireless face tracker for desktop applications
 * like VSeeFace or VMagicMirror:
 *
 * ```kotlin
 * // On the mobile device (Android/iOS)
 * val faceTracker = FaceTracker(context)
 * val sender = VmcSender(
 *     VmcConfig(
 *         host = "192.168.1.100",  // Desktop PC IP address
 *         port = 39539,             // VMC Marionette port
 *     ),
 * )
 *
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 * faceTracker.start()
 * sender.start(faceTracker.trackingData, scope)
 *
 * // When done:
 * sender.stop()
 * faceTracker.stop()
 * scope.cancel()
 * ```
 *
 * ## VRM 1.0 Expression Parameters
 *
 * When using pre-mapped VRM expression parameters (e.g., from [VrmParameterMapper]):
 *
 * ```kotlin
 * val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
 * val expressionFlow = faceTracker.trackingData.map { mapper.map(it) }
 * sender.startWithExpressions(expressionFlow, scope)
 * ```
 *
 * @param config VMC connection configuration
 * @see VmcReceiver for the receiving (Marionette) side
 * @see VmcConfig for connection settings
 */
public class VmcSender(
    private val config: VmcConfig = VmcConfig(),
) : Releasable {
    private var socket = UdpSocket()
    private var collectJob: Job? = null
    private var startTimeMs: Long = 0L

    /**
     * Starts collecting from a [FaceTrackingData] flow and sending VMC messages.
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
        socket = UdpSocket()
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
     * Starts collecting from a pre-mapped expression parameter flow and sending VMC messages.
     *
     * Use this with the output of `Flow<FaceTrackingData>.toAvatarParameters(VrmParameterMapper)`.
     * Expression keys are sent as `/VMC/Ext/Blend/Val` names directly. Head rotation keys
     * specified in [headParamKeys] are sent via `/VMC/Ext/Bone/Pos` instead.
     *
     * @param expressionFlow Flow of pre-mapped expression parameter maps
     * @param scope Coroutine scope for the collection job
     * @param headParamKeys Keys in the map that represent head rotation (yaw, pitch, roll)
     */
    public fun startWithExpressions(
        expressionFlow: Flow<Map<String, Float>>,
        scope: CoroutineScope,
        headParamKeys: VmcHeadParamKeys = VmcHeadParamKeys.VRM_1_0,
    ) {
        stop()
        socket = UdpSocket()
        startTimeMs = currentTimeMs()
        collectJob = scope.launch {
            expressionFlow.collect { params ->
                sendExpressionFrame(params, headParamKeys, currentTimeMs())
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
     * Sends a single frame of pre-mapped expression parameters immediately.
     *
     * Head rotation values (identified by [headParamKeys]) are extracted and sent
     * as a bone position message. Remaining parameters are sent as blend shape values.
     *
     * @param expressions Pre-mapped expression parameter map (e.g. from VrmParameterMapper)
     * @param headParamKeys Keys identifying head yaw/pitch/roll in the map
     * @param timestampMs Frame timestamp in milliseconds
     */
    public fun sendExpressionFrame(
        expressions: Map<String, Float>,
        headParamKeys: VmcHeadParamKeys = VmcHeadParamKeys.VRM_1_0,
        timestampMs: Long = currentTimeMs(),
    ) {
        val messages = buildExpressionFrameMessages(expressions, headParamKeys, timestampMs)
        sendMessages(messages)
    }

    /**
     * Stops collecting tracking data and closes the current UDP socket.
     *
     * Can be restarted by calling [start] or [startWithExpressions] again.
     */
    public fun stop() {
        collectJob?.cancel()
        collectJob = null
        socket.close()
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

    private fun buildExpressionFrameMessages(
        expressions: Map<String, Float>,
        headParamKeys: VmcHeadParamKeys,
        timestampMs: Long,
    ): List<OscMessage> {
        val relativeTimeSec = (timestampMs - startTimeMs).toFloat() / MS_PER_SEC
        val headKeys = setOf(headParamKeys.yaw, headParamKeys.pitch, headParamKeys.roll)
        val messages = mutableListOf<OscMessage>()
        messages.add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
        messages.addAll(VmcMessageBuilder.buildExpressionMessages(expressions, excludeKeys = headKeys))
        val headTransform = HeadTransform(
            yaw = expressions[headParamKeys.yaw] ?: 0f,
            pitch = expressions[headParamKeys.pitch] ?: 0f,
            roll = expressions[headParamKeys.roll] ?: 0f,
        )
        messages.add(VmcMessageBuilder.buildHeadBoneMessage(headTransform))
        messages.add(VmcMessageBuilder.buildTimestampMessage(relativeTimeSec))
        return messages
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
