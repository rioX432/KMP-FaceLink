package io.github.kmpfacelink.stream

import io.github.kmpfacelink.stream.internal.WebSocketProvider
import kotlinx.coroutines.channels.Channel

/**
 * Fake [WebSocketProvider] for testing VTS protocol flows.
 */
internal class FakeWebSocketProvider : WebSocketProvider {

    val sentMessages = mutableListOf<String>()
    val incomingMessages = Channel<String>(Channel.UNLIMITED)
    var connected = false
        private set
    private var onMessage: (suspend (String) -> Unit)? = null
    private var onClose: (suspend (String?) -> Unit)? = null
    private var onError: (suspend (Throwable) -> Unit)? = null

    override val isConnected: Boolean get() = connected

    override suspend fun connect(
        url: String,
        onMessage: suspend (String) -> Unit,
        onClose: suspend (reason: String?) -> Unit,
        onError: suspend (Throwable) -> Unit,
    ) {
        connected = true
        this.onMessage = onMessage
        this.onClose = onClose
        this.onError = onError

        // Process incoming messages until close
        try {
            for (msg in incomingMessages) {
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
        incomingMessages.close()
    }

    override fun release() {
        connected = false
    }

    /** Simulate receiving a message from the server. */
    suspend fun receiveFromServer(text: String) {
        incomingMessages.send(text)
    }

    /** Simulate server closing the connection. */
    fun simulateClose() {
        incomingMessages.close()
    }
}
