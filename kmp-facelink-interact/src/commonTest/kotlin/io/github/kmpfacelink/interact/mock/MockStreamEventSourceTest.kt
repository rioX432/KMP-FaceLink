package io.github.kmpfacelink.interact.mock

import io.github.kmpfacelink.interact.model.ChannelPointRedemption
import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.ConnectionState
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.Raid
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.github.kmpfacelink.interact.model.SuperChat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockStreamEventSourceTest {

    @Test
    fun eventsAreEmittedAfterConnect() = runTest {
        val source = createSource()
        val job = launch { source.connect() }
        val event = source.events.first()
        assertEquals(StreamPlatform.TWITCH, event.platform)
        source.disconnect()
        job.cancel()
    }

    @Test
    fun connectionStateTransitions() = runTest {
        val source = createSource(intervalMs = 100L)
        assertEquals(ConnectionState.DISCONNECTED, source.connectionState.value)

        val job = launch { source.connect() }
        testScheduler.runCurrent()
        assertEquals(ConnectionState.CONNECTED, source.connectionState.value)

        source.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, source.connectionState.value)
        job.cancel()
    }

    @Test
    fun initialStateIsDisconnected() = runTest {
        val source = createSource()
        assertEquals(ConnectionState.DISCONNECTED, source.connectionState.value)
    }

    @Test
    fun noEventsAfterDisconnect() = runTest {
        val source = createSource()
        val job = launch { source.connect() }
        source.events.first()
        source.disconnect()
        job.cancel()
        assertEquals(ConnectionState.DISCONNECTED, source.connectionState.value)
    }

    @Test
    fun chatMessageCanBeGenerated() = runTest {
        val config = ZERO_CONFIG.copy(chatProbability = 1.0f)
        val event = collectSingleEvent(config)
        assertTrue(event is ChatMessage, "Expected ChatMessage")
    }

    @Test
    fun giftCanBeGenerated() = runTest {
        val config = ZERO_CONFIG.copy(giftProbability = 1.0f)
        val event = collectSingleEvent(config)
        assertTrue(event is Gift, "Expected Gift")
    }

    @Test
    fun followCanBeGenerated() = runTest {
        val config = ZERO_CONFIG.copy(followProbability = 1.0f)
        val event = collectSingleEvent(config)
        assertTrue(event is Follow, "Expected Follow")
    }

    @Test
    fun raidCanBeGenerated() = runTest {
        val config = ZERO_CONFIG.copy(raidProbability = 1.0f)
        val event = collectSingleEvent(config)
        assertTrue(event is Raid, "Expected Raid")
    }

    @Test
    fun superChatCanBeGenerated() = runTest {
        val config = ZERO_CONFIG.copy(superChatProbability = 1.0f)
        val event = collectSingleEvent(config)
        assertTrue(event is SuperChat, "Expected SuperChat")
    }

    @Test
    fun channelPointRedemptionCanBeGenerated() = runTest {
        val config = ZERO_CONFIG.copy(channelPointProbability = 1.0f)
        val event = collectSingleEvent(config)
        assertTrue(event is ChannelPointRedemption, "Expected ChannelPointRedemption")
    }

    @Test
    fun platformIsSetCorrectly() = runTest {
        val source = createSource(platform = StreamPlatform.YOUTUBE)
        val job = launch { source.connect() }
        val event = source.events.first()
        assertEquals(StreamPlatform.YOUTUBE, event.platform)
        source.disconnect()
        job.cancel()
    }

    @Test
    fun multipleEventsAreEmitted() = runTest {
        val source = createSource()
        val job = launch { source.connect() }
        val events = source.events.take(MULTI_EVENT_COUNT).toList()
        assertEquals(MULTI_EVENT_COUNT, events.size)
        source.disconnect()
        job.cancel()
    }

    private suspend fun collectSingleEvent(config: MockConfig): StreamEvent {
        val source = MockStreamEventSource(
            config = config,
            random = Random(SEED),
        )
        return coroutineScope {
            val job = launch { source.connect() }
            val event = source.events.first()
            source.disconnect()
            job.cancel()
            event
        }
    }

    private fun createSource(
        intervalMs: Long = 1L,
        platform: StreamPlatform = StreamPlatform.TWITCH,
    ): MockStreamEventSource = MockStreamEventSource(
        config = MockConfig(intervalMs = intervalMs),
        platform = platform,
        random = Random(SEED),
    )
}

/** Config with all probabilities zeroed out; copy and set one to 1.0f per test. */
private val ZERO_CONFIG = MockConfig(
    intervalMs = 1L,
    chatProbability = 0.0f,
    giftProbability = 0.0f,
    followProbability = 0.0f,
    raidProbability = 0.0f,
    superChatProbability = 0.0f,
    channelPointProbability = 0.0f,
)

private const val SEED = 42
private const val MULTI_EVENT_COUNT = 5
