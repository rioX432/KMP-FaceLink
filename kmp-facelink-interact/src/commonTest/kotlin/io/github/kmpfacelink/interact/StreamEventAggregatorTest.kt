package io.github.kmpfacelink.interact

import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.ConnectionState
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamEventAggregatorTest {

    private class FakeEventSource(
        override val platform: StreamPlatform,
    ) : StreamEventSource {
        val eventFlow = MutableSharedFlow<StreamEvent>(replay = 8)
        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        override val connectionState: StateFlow<ConnectionState> = _connectionState
        override val events: Flow<StreamEvent> = eventFlow
        var connectCalled = false
        var disconnectCalled = false

        override suspend fun connect() {
            connectCalled = true
            _connectionState.value = ConnectionState.CONNECTED
        }

        override suspend fun disconnect() {
            disconnectCalled = true
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    @Test
    fun aggregatorMergesEventsFromMultipleSources() = runTest {
        val twitchSource = FakeEventSource(StreamPlatform.TWITCH)
        val youtubeSource = FakeEventSource(StreamPlatform.YOUTUBE)

        val aggregator = StreamEventAggregator()
        aggregator.addSource(twitchSource)
        aggregator.addSource(youtubeSource)

        // Emit events first (buffered in replay cache)
        twitchSource.eventFlow.emit(
            ChatMessage(
                platform = StreamPlatform.TWITCH,
                timestampMs = 1000L,
                author = "twitch_user",
                text = "Hello from Twitch",
            ),
        )
        youtubeSource.eventFlow.emit(
            Follow(
                platform = StreamPlatform.YOUTUBE,
                timestampMs = 2000L,
                author = "youtube_user",
            ),
        )

        // Collect with take(2) so the flow terminates
        val collected = aggregator.events.take(2).toList()

        assertEquals(2, collected.size)
        assertTrue(collected.any { it is ChatMessage })
        assertTrue(collected.any { it is Follow })
    }

    @Test
    fun aggregatorEmitsSingleEvent() = runTest {
        val source = FakeEventSource(StreamPlatform.TWITCH)

        val aggregator = StreamEventAggregator()
        aggregator.addSource(source)

        source.eventFlow.emit(
            ChatMessage(
                platform = StreamPlatform.TWITCH,
                timestampMs = 1000L,
                author = "viewer",
                text = "Hello",
            ),
        )

        val event = aggregator.events.first()
        assertTrue(event is ChatMessage)
        assertEquals("Hello", event.text)
    }

    @Test
    fun connectAllConnectsAllSources() = runTest {
        val source1 = FakeEventSource(StreamPlatform.TWITCH)
        val source2 = FakeEventSource(StreamPlatform.YOUTUBE)

        val aggregator = StreamEventAggregator()
        aggregator.addSource(source1)
        aggregator.addSource(source2)
        aggregator.connectAll()

        assertTrue(source1.connectCalled)
        assertTrue(source2.connectCalled)
    }

    @Test
    fun disconnectAllDisconnectsAllSources() = runTest {
        val source1 = FakeEventSource(StreamPlatform.TWITCH)
        val source2 = FakeEventSource(StreamPlatform.BILIBILI)

        val aggregator = StreamEventAggregator()
        aggregator.addSource(source1)
        aggregator.addSource(source2)
        aggregator.disconnectAll()

        assertTrue(source1.disconnectCalled)
        assertTrue(source2.disconnectCalled)
    }

    @Test
    fun removeSourceExcludesItFromAggregation() = runTest {
        val source1 = FakeEventSource(StreamPlatform.TWITCH)
        val source2 = FakeEventSource(StreamPlatform.YOUTUBE)

        val aggregator = StreamEventAggregator()
        aggregator.addSource(source1)
        aggregator.addSource(source2)
        aggregator.removeSource(source2)

        // Only source1 remains — emit and collect 1 event
        source1.eventFlow.emit(
            ChatMessage(
                platform = StreamPlatform.TWITCH,
                timestampMs = 3000L,
                author = "twitch_only",
                text = "Only Twitch",
            ),
        )

        val event = aggregator.events.first()
        assertEquals(StreamPlatform.TWITCH, event.platform)
    }
}
