package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.api.Releasable
import io.github.kmpfacelink.networking.UdpSocket
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.withLock
import io.github.kmpfacelink.vmc.osc.OscDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * VMC Protocol receiver (Marionette role).
 *
 * Listens for VMC Protocol messages over UDP and emits parsed [VmcReceivedData]
 * as a Kotlin [Flow]. This allows consuming tracking data from external VMC senders
 * such as VSeeFace, VMagicMirror, or a mobile device running [VmcSender].
 *
 * Supports both individual OSC messages and OSC bundles. Parsed data includes:
 * - Blend shape values (all 52 ARKit shapes)
 * - Head bone position and rotation (VRM 1.0 "Head" bone)
 * - Sender status (avatar loaded state)
 * - Sender timestamp for synchronization
 *
 * ## Desktop Receiver Example
 *
 * Receive face tracking data from a phone running KMP-FaceLink:
 *
 * ```kotlin
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 * val receiver = VmcReceiver(port = 39539)
 * receiver.start(scope)
 * receiver.receivedData.collect { data ->
 *     if (data.isLoaded) {
 *         // Apply blend shapes to avatar
 *         for ((shape, value) in data.blendShapes) {
 *             avatar.setBlendShape(shape.arKitName, value)
 *         }
 *         // Apply head rotation (quaternion xyzw)
 *         data.headRotation?.let { q ->
 *             avatar.setHeadRotation(q[0], q[1], q[2], q[3])
 *         }
 *     }
 * }
 * ```
 *
 * @param port UDP port to listen on. Default is [VmcConfig.DEFAULT_MARIONETTE_PORT].
 * @see VmcSender for the sending (Performer) side
 * @see VmcReceivedData for the parsed data structure
 */
public class VmcReceiver(
    private val port: Int = VmcConfig.DEFAULT_MARIONETTE_PORT,
) : Releasable {
    private val socketLock = PlatformLock()
    private var socket = UdpSocket()
    private var listenJob: Job? = null
    private val _receivedData = MutableSharedFlow<VmcReceivedData>(extraBufferCapacity = 1)

    /**
     * Flow of received VMC data. Emits a new [VmcReceivedData] for each
     * incoming packet or bundle.
     */
    public val receivedData: Flow<VmcReceivedData> = _receivedData.asSharedFlow()

    /**
     * Starts listening for VMC messages on the configured port.
     *
     * The caller owns [scope] and is responsible for cancelling it
     * when receiving is no longer needed.
     *
     * @param scope Coroutine scope for the listener job
     */
    public fun start(scope: CoroutineScope) {
        stop()
        val newSocket = UdpSocket()
        newSocket.bind(port)
        socketLock.withLock { socket = newSocket }
        listenJob = scope.launch {
            receiveLoop()
        }
    }

    /**
     * Stops listening and closes the current UDP socket.
     *
     * The socket is closed first so that any blocking [UdpSocket.receive] call
     * throws immediately, allowing the coroutine to observe cancellation without
     * waiting for the next packet. The job is then cancelled and cleared.
     *
     * Can be restarted by calling [start] again.
     */
    public fun stop() {
        socketLock.withLock { socket.close() }
        listenJob?.cancel()
        listenJob = null
    }

    /**
     * Releases all resources held by this receiver.
     *
     * Stops listening and closes the UDP socket.
     * Safe to call multiple times.
     */
    public override fun release() {
        stop()
    }

    private suspend fun CoroutineScope.receiveLoop() {
        var backoffMs = 0L
        while (isActive) {
            try {
                if (backoffMs > 0L) {
                    delay(backoffMs)
                }
                val currentSocket = socketLock.withLock { socket }
                val packetData = currentSocket.receive()
                val messages = OscDecoder.decode(packetData)
                val parsed = VmcMessageParser.parse(messages)
                _receivedData.tryEmit(parsed)
                backoffMs = 0L
            } catch (expected: Exception) {
                // On stop(), the socket is closed before the job is cancelled.
                // Closing the socket causes the blocking receive() to throw
                // (SocketException on JVM, IllegalStateException on iOS via check()).
                // If the scope is no longer active, treat this as a normal shutdown.
                if (!isActive) break
                FaceLinkLogger.w(TAG, "Unexpected socket error: ${expected.message}", expected)
                backoffMs = if (backoffMs == 0L) {
                    INITIAL_BACKOFF_MS
                } else {
                    (backoffMs * BACKOFF_MULTIPLIER).coerceAtMost(MAX_BACKOFF_MS)
                }
            }
        }
    }

    private companion object {
        private const val TAG = "VmcReceiver"
        private const val INITIAL_BACKOFF_MS = 100L
        private const val MAX_BACKOFF_MS = 5000L
        private const val BACKOFF_MULTIPLIER = 2
    }
}
