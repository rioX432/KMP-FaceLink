package io.github.kmpfacelink.interact.youtube

import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.ConnectionState
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.github.kmpfacelink.interact.model.SuperChat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YouTubeLiveChatSourceTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, "application/json")

    private fun createNoOpClient(): HttpClient {
        val engine = MockEngine {
            respond(EMPTY_POLL_RESPONSE, HttpStatusCode.OK, jsonHeaders)
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun createSourceWithEngine(
        config: YouTubeConfig = DEFAULT_CONFIG,
        engine: MockEngine,
    ): YouTubeLiveChatSource {
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return YouTubeLiveChatSource(config, client)
    }

    @Test
    fun pollingIntervalEnforcesMaxPollsPerMinute() {
        val config = YouTubeConfig(
            apiKey = "key",
            liveChatId = "chat1",
            maxPollsPerMinute = 6,
        )
        val source = YouTubeLiveChatSource(config, createNoOpClient())

        // API says 5s, but max 6 polls/min = 10s minimum
        assertEquals(10_000L, source.enforceMinInterval(5_000L))

        // API says 15s, which is longer than 10s minimum, so use API value
        assertEquals(15_000L, source.enforceMinInterval(15_000L))
    }

    @Test
    fun pollingIntervalWithHigherMaxPolls() {
        val config = YouTubeConfig(
            apiKey = "key",
            liveChatId = "chat1",
            maxPollsPerMinute = 12,
        )
        val source = YouTubeLiveChatSource(config, createNoOpClient())

        // 12 polls/min = 5s minimum; API says 3s -> use 5s
        assertEquals(5_000L, source.enforceMinInterval(3_000L))

        // API says 10s, which is longer than 5s -> use API value
        assertEquals(10_000L, source.enforceMinInterval(10_000L))
    }

    @Test
    fun pollingIntervalWithOneMaxPoll() {
        val config = YouTubeConfig(
            apiKey = "key",
            liveChatId = "chat1",
            maxPollsPerMinute = 1,
        )
        val source = YouTubeLiveChatSource(config, createNoOpClient())

        // 1 poll/min = 60s minimum
        assertEquals(60_000L, source.enforceMinInterval(5_000L))
    }

    @Test
    fun connectEmitsEventsFromMockApi() = runTest {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("liveChat/messages") ->
                    respond(POLL_RESPONSE_WITH_MESSAGES, HttpStatusCode.OK, jsonHeaders)
                else -> respond("{}", status = HttpStatusCode.NotFound)
            }
        }
        val source = createSourceWithEngine(engine = engine)
        val events = mutableListOf<StreamEvent>()

        backgroundScope.launch(Dispatchers.Default) {
            source.events.collect { events.add(it) }
        }
        backgroundScope.launch(Dispatchers.Default) { source.connect() }
        withContext(Dispatchers.Default) {
            kotlinx.coroutines.delay(POLL_SETTLE_MS)
        }

        assertTrue(events.isNotEmpty(), "Expected events to be emitted")
        val first = events[0]
        assertTrue(first is ChatMessage)
        assertEquals("Hello world!", first.text)
        assertEquals("ChatUser", first.author)
        assertEquals(StreamPlatform.YOUTUBE, first.platform)

        source.disconnect()
    }

    @Test
    fun connectWithSuperChatEvent() = runTest {
        val engine = MockEngine {
            respond(POLL_RESPONSE_WITH_SUPER_CHAT, HttpStatusCode.OK, jsonHeaders)
        }
        val source = createSourceWithEngine(engine = engine)
        val events = mutableListOf<StreamEvent>()

        backgroundScope.launch(Dispatchers.Default) {
            source.events.collect { events.add(it) }
        }
        backgroundScope.launch(Dispatchers.Default) { source.connect() }
        withContext(Dispatchers.Default) {
            kotlinx.coroutines.delay(POLL_SETTLE_MS)
        }

        assertTrue(events.isNotEmpty(), "Expected events to be emitted")
        val first = events[0]
        assertTrue(first is SuperChat)
        assertEquals("Donor", first.author)
        assertEquals(10.0, first.amount)
        assertEquals("USD", first.currency)
        assertEquals("Nice!", first.text)

        source.disconnect()
    }

    @Test
    fun autoDetectLiveChatIdFromBroadcast() = runTest {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("liveBroadcasts") ->
                    respond(BROADCAST_RESPONSE, HttpStatusCode.OK, jsonHeaders)
                url.contains("liveChat/messages") ->
                    respond(POLL_RESPONSE_WITH_MESSAGES, HttpStatusCode.OK, jsonHeaders)
                else -> respond("{}", status = HttpStatusCode.NotFound)
            }
        }
        val autoDetectConfig = YouTubeConfig(
            apiKey = "test-key",
            liveChatId = null,
            accessToken = "oauth-token",
        )
        val source = createSourceWithEngine(
            config = autoDetectConfig,
            engine = engine,
        )
        val events = mutableListOf<StreamEvent>()

        backgroundScope.launch(Dispatchers.Default) {
            source.events.collect { events.add(it) }
        }
        backgroundScope.launch(Dispatchers.Default) { source.connect() }
        withContext(Dispatchers.Default) {
            kotlinx.coroutines.delay(POLL_SETTLE_MS)
        }

        assertTrue(events.isNotEmpty(), "Expected events to be emitted")
        assertTrue(events[0] is ChatMessage)
        source.disconnect()
    }

    @Test
    fun disconnectSetsStateToDisconnected() = runTest {
        val config = YouTubeConfig(apiKey = "key", liveChatId = "chat1")
        val source = YouTubeLiveChatSource(config, createNoOpClient())
        assertEquals(ConnectionState.DISCONNECTED, source.connectionState.value)
    }

    @Test
    fun configDefaultMaxPolls() {
        val config = YouTubeConfig(apiKey = "key")
        assertEquals(DEFAULT_MAX_POLLS_PER_MINUTE, config.maxPollsPerMinute)
    }

    @Test
    fun configCustomValues() {
        val config = YouTubeConfig(
            apiKey = "my-key",
            liveChatId = "chat-abc",
            accessToken = "token-xyz",
            maxPollsPerMinute = 10,
        )
        assertEquals("my-key", config.apiKey)
        assertEquals("chat-abc", config.liveChatId)
        assertEquals("token-xyz", config.accessToken)
        assertEquals(10, config.maxPollsPerMinute)
    }

    @Test
    fun platformIsYoutube() {
        val config = YouTubeConfig(apiKey = "key", liveChatId = "chat1")
        val source = YouTubeLiveChatSource(config, createNoOpClient())
        assertEquals(StreamPlatform.YOUTUBE, source.platform)
    }

    companion object {
        private const val POLL_SETTLE_MS = 1_000L

        private val DEFAULT_CONFIG = YouTubeConfig(
            apiKey = "test-key",
            liveChatId = "chat123",
        )

        private val EMPTY_POLL_RESPONSE = """
            {
                "kind": "youtube#liveChatMessageListResponse",
                "nextPageToken": "token",
                "pollingIntervalMillis": 10000,
                "items": []
            }
        """.trimIndent()

        private val POLL_RESPONSE_WITH_MESSAGES = """
            {
                "kind": "youtube#liveChatMessageListResponse",
                "nextPageToken": "page2",
                "pollingIntervalMillis": 10000,
                "pageInfo": {
                    "totalResults": 1,
                    "resultsPerPage": 200
                },
                "items": [
                    {
                        "snippet": {
                            "type": "textMessageEvent",
                            "liveChatId": "chat123",
                            "authorChannelId": "UC001",
                            "publishedAt": "2023-11-14T12:00:00Z",
                            "displayMessage": "Hello world!",
                            "textMessageDetails": {
                                "messageText": "Hello world!"
                            }
                        },
                        "authorDetails": {
                            "displayName": "ChatUser",
                            "channelId": "UC001",
                            "isChatOwner": false,
                            "isChatModerator": false,
                            "isChatSponsor": false
                        }
                    }
                ]
            }
        """.trimIndent()

        private val POLL_RESPONSE_WITH_SUPER_CHAT = """
            {
                "kind": "youtube#liveChatMessageListResponse",
                "nextPageToken": "page2",
                "pollingIntervalMillis": 8000,
                "pageInfo": {
                    "totalResults": 1,
                    "resultsPerPage": 200
                },
                "items": [
                    {
                        "snippet": {
                            "type": "superChatEvent",
                            "liveChatId": "chat123",
                            "authorChannelId": "UC002",
                            "publishedAt": "2023-11-14T12:05:00Z",
                            "displayMessage": "Nice!",
                            "superChatDetails": {
                                "amountMicros": 10000000,
                                "currency": "USD",
                                "amountDisplayString": "${'$'}10.00",
                                "userComment": "Nice!",
                                "tier": 3
                            }
                        },
                        "authorDetails": {
                            "displayName": "Donor",
                            "channelId": "UC002"
                        }
                    }
                ]
            }
        """.trimIndent()

        private val BROADCAST_RESPONSE = """
            {
                "kind": "youtube#liveBroadcastListResponse",
                "pageInfo": {
                    "totalResults": 1,
                    "resultsPerPage": 5
                },
                "items": [
                    {
                        "kind": "youtube#liveBroadcast",
                        "id": "broadcast1",
                        "snippet": {
                            "liveChatId": "detected-chat-id",
                            "title": "My Live Stream"
                        }
                    }
                ]
            }
        """.trimIndent()
    }
}
