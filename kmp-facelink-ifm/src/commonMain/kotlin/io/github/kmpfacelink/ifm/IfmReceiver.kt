package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.api.Releasable
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.networking.UdpSocket
import io.github.kmpfacelink.networking.currentTimeMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * iFacialMocap protocol receiver.
 *
 * Listens for iFacialMocap data on a UDP port and emits parsed
 * [FaceTrackingData] as a Kotlin [Flow]. Can also send the
 * handshake string to an iOS device to initiate data streaming.
 *
 * Usage:
 * ```kotlin
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 * val receiver = IfmReceiver()
 * receiver.start(scope)
 * receiver.sendHandshake("192.168.1.50") // iOS device IP
 * receiver.trackingData.collect { data ->
 *     // Use data.blendShapes, data.headTransform, etc.
 * }
 * ```
 *
 * @param port UDP port to listen on and receive data.
 *             Default is [IfmConfig.DEFAULT_PORT] (49983).
 */
public class IfmReceiver(
    private val port: Int = IfmConfig.DEFAULT_PORT,
) : Releasable {
    private val socket = UdpSocket()
    private var listenJob: Job? = null
    private val _trackingData = MutableSharedFlow<FaceTrackingData>(extraBufferCapacity = 1)

    /**
     * Flow of received face tracking data parsed from iFacialMocap messages.
     */
    public val trackingData: Flow<FaceTrackingData> = _trackingData.asSharedFlow()

    /**
     * Starts listening for iFacialMocap messages on the configured port.
     *
     * The caller owns [scope] and is responsible for cancelling it
     * when receiving is no longer needed.
     *
     * @param scope Coroutine scope for the listener job
     */
    public fun start(scope: CoroutineScope) {
        stop()
        socket.bind(port)
        listenJob = scope.launch {
            receiveLoop()
        }
    }

    /**
     * Sends the iFacialMocap handshake string to an iOS device,
     * requesting it to start streaming data back to this receiver.
     *
     * @param deviceHost IP address of the iOS device running iFacialMocap
     * @param devicePort Port on the iOS device. Default is [IfmConfig.DEFAULT_PORT].
     */
    public fun sendHandshake(
        deviceHost: String,
        devicePort: Int = IfmConfig.DEFAULT_PORT,
    ) {
        val handshakeBytes = HANDSHAKE_STRING.encodeToByteArray()
        socket.send(handshakeBytes, deviceHost, devicePort)
    }

    /**
     * Stops listening and releases the UDP socket.
     */
    public fun stop() {
        listenJob?.cancel()
        listenJob = null
        socket.close()
    }

    /** Releases all resources by stopping the receiver. */
    override fun release() {
        stop()
    }

    private suspend fun CoroutineScope.receiveLoop() {
        while (isActive) {
            try {
                val packetData = socket.receive()
                val message = packetData.decodeToString()
                if (message == HANDSHAKE_STRING) continue
                val parsed = IfmMessageParser.parse(message)
                val trackingData = FaceTrackingData(
                    blendShapes = parsed.blendShapes,
                    headTransform = parsed.headTransform,
                    gazeData = parsed.gazeData,
                    timestampMs = currentTimeMs(),
                    isTracking = parsed.blendShapes.isNotEmpty(),
                )
                _trackingData.tryEmit(trackingData)
            } catch (expected: IllegalStateException) {
                if (!isActive) break
            }
        }
    }
}

/**
 * iFacialMocap magic handshake string.
 * Sending this to port 49983 on an iOS device triggers data streaming.
 */
public const val HANDSHAKE_STRING: String =
    "iFacialMocap_sahuasouryya9218sauhuiayeta91555dy3719"
