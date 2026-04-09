package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.api.Releasable
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.networking.UdpSocket
import io.github.kmpfacelink.networking.currentTimeMs
import io.github.kmpfacelink.util.FaceLinkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var socket = UdpSocket()
    private var listenJob: Job? = null
    private val _trackingData = MutableSharedFlow<FaceTrackingData>(extraBufferCapacity = 1)

    /**
     * Flow of received face tracking data parsed from iFacialMocap messages.
     */
    public val trackingData: Flow<FaceTrackingData> = _trackingData.asSharedFlow()

    /**
     * Starts listening for iFacialMocap messages on the configured port.
     *
     * Creates a fresh UDP socket on each start, ensuring clean state
     * after connection drops. The caller owns [scope] and is responsible
     * for cancelling it when receiving is no longer needed.
     *
     * @param scope Coroutine scope for the listener job
     */
    public fun start(scope: CoroutineScope) {
        stop()
        socket = UdpSocket()
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
     * Stops listening and closes the current UDP socket.
     *
     * Can be restarted by calling [start] again.
     */
    public fun stop() {
        socket.close()
        listenJob?.cancel()
        listenJob = null
    }

    /**
     * Releases all resources held by this receiver.
     *
     * Stops listening and closes the UDP socket.
     * Safe to call multiple times.
     */
    override fun release() {
        stop()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun CoroutineScope.receiveLoop() {
        var backoffMs = 0L
        while (isActive) {
            try {
                if (backoffMs > 0L) {
                    delay(backoffMs)
                }
                val packetData = socket.receive()
                backoffMs = processPacket(packetData, backoffMs)
            } catch (expected: IllegalStateException) {
                if (!isActive) return
                FaceLinkLogger.w(TAG, "Socket error: ${expected.message}", expected)
                backoffMs = nextBackoff(backoffMs)
            } catch (expected: kotlinx.coroutines.CancellationException) {
                throw expected
            } catch (expected: Exception) {
                if (!isActive) return
                FaceLinkLogger.e(TAG, "Unexpected error in receive loop: ${expected.message}", expected)
                backoffMs = nextBackoff(backoffMs)
            }
        }
    }

    private fun processPacket(packetData: ByteArray, currentBackoff: Long): Long {
        val message = packetData.decodeToString()
        if (message == HANDSHAKE_STRING) return currentBackoff
        val parsed = IfmMessageParser.parse(message)
        val trackingData = FaceTrackingData(
            blendShapes = parsed.blendShapes,
            headTransform = parsed.headTransform,
            gazeData = parsed.gazeData,
            timestampMs = currentTimeMs(),
            isTracking = parsed.blendShapes.isNotEmpty(),
        )
        _trackingData.tryEmit(trackingData)
        return 0L
    }

    private companion object {
        private const val TAG = "IfmReceiver"
        private const val INITIAL_BACKOFF_MS = 100L
        private const val MAX_BACKOFF_MS = 5000L
        private const val BACKOFF_MULTIPLIER = 2

        private fun nextBackoff(current: Long): Long =
            if (current == 0L) {
                INITIAL_BACKOFF_MS
            } else {
                (current * BACKOFF_MULTIPLIER).coerceAtMost(MAX_BACKOFF_MS)
            }
    }
}

/**
 * iFacialMocap magic handshake string.
 * Sending this to port 49983 on an iOS device triggers data streaming.
 */
public const val HANDSHAKE_STRING: String =
    "iFacialMocap_sahuasouryya9218sauhuiayeta91555dy3719"
