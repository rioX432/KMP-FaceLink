package io.github.kmpfacelink.interact

import io.github.kmpfacelink.interact.model.ConnectionState
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A source of [StreamEvent]s from a single streaming platform.
 *
 * Implementations connect to a platform's real-time API (e.g. Twitch EventSub,
 * YouTube Live Chat) and emit events as a [Flow]. The [connectionState] allows
 * UI to reflect connectivity status.
 */
public interface StreamEventSource {
    /** Hot flow of events from this source. */
    public val events: Flow<StreamEvent>

    /** Current connection state. */
    public val connectionState: StateFlow<ConnectionState>

    /** The platform this source connects to. */
    public val platform: StreamPlatform

    /** Establish connection to the platform. */
    public suspend fun connect()

    /** Gracefully disconnect from the platform. */
    public suspend fun disconnect()
}
