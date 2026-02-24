package io.github.kmpfacelink.stream.internal

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.consumeEach
import kotlin.concurrent.Volatile

/**
 * Ktor-based [WebSocketProvider] implementation.
 *
 * Uses the platform-specific Ktor engine (OkHttp on Android, Darwin on iOS).
 */
internal class KtorWebSocketProvider(
    private val client: HttpClient = HttpClient(platformEngine()) {
        install(WebSockets)
    },
) : WebSocketProvider {

    @Volatile
    private var session: WebSocketSession? = null

    override val isConnected: Boolean
        get() = session != null

    override suspend fun connect(
        url: String,
        onMessage: suspend (String) -> Unit,
        onClose: suspend (reason: String?) -> Unit,
        onError: suspend (Throwable) -> Unit,
    ) {
        try {
            client.webSocket(url) {
                session = this
                try {
                    incoming.consumeEach { frame: Frame ->
                        if (frame is Frame.Text) {
                            onMessage(frame.readText())
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    onError(e)
                }
            }
            session = null
            onClose(null)
        } catch (e: CancellationException) {
            session = null
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            session = null
            onError(e)
        }
    }

    override suspend fun send(text: String) {
        session?.send(Frame.Text(text))
    }

    override suspend fun close() {
        session?.close()
        session = null
    }

    override fun release() {
        session = null
        client.close()
    }
}
