package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.networking.UdpSocket
import io.github.kmpfacelink.vmc.osc.OscDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * such as VSeeFace, VMagicMirror, or other VMC-compatible software.
 *
 * Usage:
 * ```kotlin
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 * val receiver = VmcReceiver(port = 39539)
 * receiver.start(scope)
 * receiver.receivedData.collect { data ->
 *     // Use data.blendShapes, data.headPosition, etc.
 * }
 * ```
 *
 * @param port UDP port to listen on. Default is [VmcConfig.DEFAULT_MARIONETTE_PORT].
 */
public class VmcReceiver(
    private val port: Int = VmcConfig.DEFAULT_MARIONETTE_PORT,
) {
    private val socket = UdpSocket()
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
        socket.bind(port)
        listenJob = scope.launch {
            receiveLoop()
        }
    }

    /**
     * Stops listening and releases the UDP socket.
     */
    public fun stop() {
        listenJob?.cancel()
        listenJob = null
        socket.close()
    }

    private suspend fun CoroutineScope.receiveLoop() {
        while (isActive) {
            try {
                val packetData = socket.receive()
                val messages = OscDecoder.decode(packetData)
                val parsed = VmcMessageParser.parse(messages)
                _receivedData.tryEmit(parsed)
            } catch (expected: IllegalStateException) {
                // Socket closed or decode error — exit if scope is cancelled
                if (!isActive) break
            }
        }
    }
}
