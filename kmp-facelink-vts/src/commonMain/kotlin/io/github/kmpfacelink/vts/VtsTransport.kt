package io.github.kmpfacelink.vts

/**
 * Internal transport interface used by [VtsAuthenticator] and [VtsParameterInjector]
 * to send requests and receive responses over the WebSocket connection.
 */
internal interface VtsTransport {
    /** Sends a request JSON string and awaits the response envelope. */
    suspend fun sendAndReceive(request: String): VtsEnvelope

    /** Returns a unique, monotonically increasing request ID string. */
    fun nextRequestId(): String
}
