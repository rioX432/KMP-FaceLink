package io.github.kmpfacelink.stream

import io.github.kmpfacelink.stream.internal.WebSocketProvider
import kotlinx.coroutines.channels.Channel

/**
 * Fake [WebSocketProvider] for testing VTS protocol flows.
 *
 * Supports multiple connect/disconnect cycles for reconnection testing.
 *
 * Usage patterns:
 * - [receiveFromServer] — push a server message into the active session
 * - [simulateClose] — cleanup-only close (does NOT fire onClose; for test teardown)
 * - [triggerServerClose] — fires onClose callback (simulates server-initiated disconnect)
 * - [simulateError] — fires onError callback (simulates network error)
 * - [connectError] — set to make the next [connect] call throw immediately
 */
internal class FakeWebSocketProvider : WebSocketProvider {

    val sentMessages = mutableListOf<String>()
    var connectCount = 0
        private set
    var connected = false
        private set

    /**
     * When non-null, every [connect] call will throw this error instead of connecting.
     * Set to null to restore normal behavior.
     */
    var connectError: Throwable? = null

    private var onMessage: (suspend (String) -> Unit)? = null
    private var onClose: (suspend (String?) -> Unit)? = null
    private var onError: (suspend (Throwable) -> Unit)? = null

    // Each connect() call drains this channel.
    // Replaced by triggerServerClose() so that a reconnect attempt gets a fresh channel.
    private var currentChannel = Channel<String>(Channel.UNLIMITED)

    override val isConnected: Boolean get() = connected

    override suspend fun connect(
        url: String,
        onMessage: suspend (String) -> Unit,
        onClose: suspend (reason: String?) -> Unit,
        onError: suspend (Throwable) -> Unit,
    ) {
        val error = connectError
        if (error != null) {
            throw error
        }

        connectCount++
        connected = true
        this.onMessage = onMessage
        this.onClose = onClose
        this.onError = onError

        // Process incoming messages until the channel is closed for this session.
        try {
            for (msg in currentChannel) {
                onMessage(msg)
            }
        } finally {
            connected = false
        }
    }

    override suspend fun send(text: String) {
        sentMessages.add(text)
    }

    override suspend fun close() {
        connected = false
        currentChannel.close()
    }

    override fun release() {
        connected = false
        currentChannel.close()
    }

    // --- Test helpers ---

    /** Simulate receiving a message from the server. */
    suspend fun receiveFromServer(text: String) {
        currentChannel.send(text)
    }

    /**
     * Closes the current channel without invoking the onClose callback.
     * Use this for test teardown to unblock [connect]'s for-loop.
     */
    fun simulateClose() {
        currentChannel.close()
    }

    /**
     * Simulates a server-initiated connection close.
     *
     * Closes the current channel (ending [connect]'s for-loop), rotates to a fresh
     * channel so a subsequent reconnection attempt can [connect] again, and then
     * invokes the [onClose] callback so the client's disconnect handler fires.
     */
    suspend fun triggerServerClose(reason: String? = null) {
        currentChannel.close()
        currentChannel = Channel(Channel.UNLIMITED)
        onClose?.invoke(reason)
    }

    /**
     * Simulates a network-level error on the active connection.
     * Invokes the onError callback registered by the most recent [connect] call.
     */
    suspend fun simulateError(error: Throwable) {
        onError?.invoke(error)
    }
}
