package io.github.kmpfacelink.stream.livekit

import io.github.kmpfacelink.stream.StreamFrame
import kotlinx.coroutines.CoroutineScope

/**
 * Abstraction for publishing tracking data to external real-time systems.
 *
 * Implementations adapt [StreamFrame] data to a target protocol (e.g. LiveKit
 * data channels, WebRTC, custom servers) and handle connection lifecycle.
 */
public interface TrackingDataPublisher {

    /** Whether the publisher is currently connected and able to send data. */
    public val isConnected: Boolean

    /**
     * Connects to the remote endpoint.
     *
     * @param scope Coroutine scope for the connection lifecycle.
     *              The caller owns this scope and is responsible for cancellation.
     */
    public suspend fun connect(scope: CoroutineScope)

    /**
     * Publishes a single tracking frame.
     *
     * Implementations should convert the [StreamFrame] into the target
     * protocol's expected format before sending.
     *
     * @param frame The tracking data frame to publish
     */
    public suspend fun publish(frame: StreamFrame)

    /**
     * Disconnects from the remote endpoint and releases resources.
     */
    public suspend fun disconnect()
}
