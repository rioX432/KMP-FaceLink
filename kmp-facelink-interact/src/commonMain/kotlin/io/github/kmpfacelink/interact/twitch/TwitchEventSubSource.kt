package io.github.kmpfacelink.interact.twitch

import io.github.kmpfacelink.interact.StreamEventSource
import io.github.kmpfacelink.interact.model.ConnectionState
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.coroutineContext

/**
 * [StreamEventSource] implementation for Twitch EventSub via WebSocket.
 *
 * Connects to the Twitch EventSub WebSocket endpoint, subscribes to the
 * configured event types via the Helix API, and emits [StreamEvent]s.
 *
 * Handles reconnection automatically: Twitch-initiated reconnects via
 * `session_reconnect` messages, and unexpected disconnects with exponential
 * backoff (1s -> 2s -> 4s -> 8s, max 30s, up to 10 attempts).
 *
 * @param config Twitch API configuration.
 * @param httpClient Optional Ktor HttpClient (must have WebSockets installed).
 */
public class TwitchEventSubSource(
    private val config: TwitchConfig,
    private val httpClient: HttpClient = createDefaultClient(),
) : StreamEventSource {

    private val json = Json { ignoreUnknownKeys = true }
    private val _events = MutableSharedFlow<StreamEvent>(extraBufferCapacity = EVENT_BUFFER)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private var connectionScope: CoroutineScope? = null
    private var connectionJob: Job? = null
    private var reconnectAttempts = 0

    override val events: Flow<StreamEvent> = _events.asSharedFlow()
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override val platform: StreamPlatform = StreamPlatform.TWITCH

    override suspend fun connect() {
        disconnect()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        connectionScope = scope
        _connectionState.value = ConnectionState.CONNECTING
        connectionJob = scope.launch { connectWebSocket(EVENTSUB_WSS_URL) }
    }

    override suspend fun disconnect() {
        connectionScope?.cancel()
        connectionScope = null
        connectionJob = null
        reconnectAttempts = 0
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun connectWebSocket(url: String) {
        try {
            httpClient.webSocket(url) { handleSession(this) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            handleUnexpectedDisconnect()
        }
    }

    private suspend fun handleSession(session: WebSocketSession) {
        val sessionId = awaitWelcome(session) ?: run {
            handleUnexpectedDisconnect()
            return
        }
        subscribeToEvents(sessionId)
        _connectionState.value = ConnectionState.CONNECTED
        reconnectAttempts = 0
        receiveLoop(session)
    }

    private suspend fun awaitWelcome(session: WebSocketSession): String? {
        for (frame in session.incoming) {
            if (frame !is Frame.Text) continue
            val msg = json.parseToJsonElement(frame.readText()).jsonObject
            if (msg.messageType() == "session_welcome") {
                return msg.sessionId()
            }
        }
        return null
    }

    private suspend fun receiveLoop(session: WebSocketSession) {
        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val msg = json.parseToJsonElement(frame.readText()).jsonObject
                processMessage(msg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: ClosedReceiveChannelException) {
            handleUnexpectedDisconnect()
        } catch (_: IOException) {
            handleUnexpectedDisconnect()
        }
    }

    private suspend fun processMessage(msg: JsonObject) {
        when (msg.messageType()) {
            "notification" -> processNotification(msg)
            "session_reconnect" -> handleReconnect(msg)
            "session_keepalive" -> Unit
            else -> Unit
        }
    }

    private fun processNotification(msg: JsonObject) {
        val metadata = msg["metadata"]?.jsonObject ?: return
        val subType = metadata["subscription_type"]?.jsonPrimitive?.content ?: return
        val event = msg["payload"]?.jsonObject?.get("event")?.jsonObject ?: return
        val timestamp = metadata["message_timestamp"]?.jsonPrimitive?.content
        val streamEvent = TwitchEventMapper.map(subType, event, parseTimestamp(timestamp))
        if (streamEvent != null) {
            _events.tryEmit(streamEvent)
        }
    }

    private suspend fun handleReconnect(msg: JsonObject) {
        val reconnectUrl = msg["payload"]?.jsonObject
            ?.get("session")?.jsonObject
            ?.get("reconnect_url")?.jsonPrimitive?.content ?: return
        _connectionState.value = ConnectionState.RECONNECTING
        connectionScope?.launch { connectWebSocket(reconnectUrl) }
    }

    private suspend fun handleUnexpectedDisconnect() {
        if (_connectionState.value == ConnectionState.DISCONNECTED) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        _connectionState.value = ConnectionState.RECONNECTING
        val delayMs = calculateBackoff(reconnectAttempts)
        reconnectAttempts++
        delay(delayMs)
        connectionScope?.launch { connectWebSocket(EVENTSUB_WSS_URL) }
    }

    private suspend fun subscribeToEvents(sessionId: String) {
        for (sub in config.subscriptions) {
            createSubscription(sessionId, sub)
        }
    }

    private suspend fun createSubscription(
        sessionId: String,
        sub: TwitchSubscriptionType,
    ) {
        val body = buildSubscriptionBody(sessionId, sub)
        httpClient.post(HELIX_SUBSCRIPTIONS_URL) {
            header("Authorization", "Bearer ${config.accessToken}")
            header("Client-Id", config.clientId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()
    }

    private fun buildSubscriptionBody(
        sessionId: String,
        sub: TwitchSubscriptionType,
    ): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("type", sub.type)
            put("version", sub.version)
            putJsonObject("condition") { addConditionFields(sub) }
            putJsonObject("transport") {
                put("method", "websocket")
                put("session_id", sessionId)
            }
        },
    )

    private fun JsonObjectBuilder.addConditionFields(sub: TwitchSubscriptionType) {
        when (sub) {
            TwitchSubscriptionType.CHAT_MESSAGE -> {
                put("broadcaster_user_id", config.broadcasterId)
                put("user_id", config.broadcasterId)
            }
            TwitchSubscriptionType.FOLLOW -> {
                put("broadcaster_user_id", config.broadcasterId)
                put("moderator_user_id", config.broadcasterId)
            }
            TwitchSubscriptionType.RAID -> {
                put("to_broadcaster_user_id", config.broadcasterId)
            }
            else -> {
                put("broadcaster_user_id", config.broadcasterId)
            }
        }
    }
}

private fun JsonObject.messageType(): String =
    this["metadata"]?.jsonObject
        ?.get("message_type")?.jsonPrimitive?.content.orEmpty()

private fun JsonObject.sessionId(): String? =
    this["payload"]?.jsonObject
        ?.get("session")?.jsonObject
        ?.get("id")?.jsonPrimitive?.content

private fun parseTimestamp(timestamp: String?): Long {
    if (timestamp == null) return FALLBACK_TIMESTAMP_MS
    return try {
        parseRfc3339(timestamp)
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        FALLBACK_TIMESTAMP_MS
    }
}

private fun parseRfc3339(input: String): Long {
    val cleaned = input.trimEnd('Z', 'z')
    val parts = cleaned.split('T')
    if (parts.size != DATE_TIME_SPLIT) return FALLBACK_TIMESTAMP_MS
    val dateParts = parts[0].split('-')
    val timeParts = parts[1].split(':')
    if (dateParts.size != EXPECTED_PARTS || timeParts.size != EXPECTED_PARTS) {
        return FALLBACK_TIMESTAMP_MS
    }
    return calculateEpochMs(dateParts, timeParts)
}

private fun calculateEpochMs(dateParts: List<String>, timeParts: List<String>): Long {
    val year = dateParts[0].toInt()
    val month = dateParts[1].toInt()
    val day = dateParts[2].toInt()
    val hour = timeParts[0].toInt()
    val minute = timeParts[1].toInt()
    val second = timeParts[2].split('.')[0].toInt()
    val days = daysSinceEpoch(year, month, day)
    val totalSeconds = days * SECONDS_PER_DAY +
        hour * SECONDS_PER_HOUR +
        minute * SECONDS_PER_MINUTE + second
    return totalSeconds * MS_PER_SECOND
}

private fun daysSinceEpoch(year: Int, month: Int, day: Int): Long {
    var days = 0L
    for (y in EPOCH_YEAR until year) {
        days += if (isLeapYear(y)) LEAP_YEAR_DAYS else REGULAR_YEAR_DAYS
    }
    val monthDays = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    if (isLeapYear(year)) monthDays[2] = LEAP_FEB_DAYS
    for (m in 1 until month) {
        days += monthDays[m]
    }
    return days + day - 1
}

private fun isLeapYear(year: Int): Boolean =
    (year % LEAP_4 == 0 && year % LEAP_100 != 0) || year % LEAP_400 == 0

internal fun calculateBackoff(attempt: Int): Long {
    val delay = INITIAL_BACKOFF_MS shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT)
    return delay.coerceAtMost(MAX_BACKOFF_MS)
}

private fun createDefaultClient(): HttpClient = HttpClient {
    install(WebSockets)
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

// Fallback timestamp when Twitch payload parsing fails.
// Actual timestamps come from the Twitch EventSub message metadata.
private const val FALLBACK_TIMESTAMP_MS = 0L

private const val EVENTSUB_WSS_URL = "wss://eventsub.wss.twitch.tv/ws"
private const val HELIX_SUBSCRIPTIONS_URL =
    "https://api.twitch.tv/helix/eventsub/subscriptions"
private const val EVENT_BUFFER = 64
private const val MAX_RECONNECT_ATTEMPTS = 10
private const val INITIAL_BACKOFF_MS = 1000L
private const val MAX_BACKOFF_MS = 30_000L
private const val MAX_BACKOFF_SHIFT = 5
private const val DATE_TIME_SPLIT = 2
private const val EXPECTED_PARTS = 3
private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_MINUTE = 60
private const val MS_PER_SECOND = 1000L
private const val EPOCH_YEAR = 1970
private const val LEAP_YEAR_DAYS = 366L
private const val REGULAR_YEAR_DAYS = 365L
private const val LEAP_FEB_DAYS = 29
private const val LEAP_4 = 4
private const val LEAP_100 = 100
private const val LEAP_400 = 400
