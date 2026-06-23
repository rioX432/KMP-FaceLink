package io.github.kmpfacelink.interact.youtube

import io.github.kmpfacelink.interact.StreamEventSource
import io.github.kmpfacelink.interact.model.ConnectionState
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.github.kmpfacelink.util.FaceLinkLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.coroutineContext

/**
 * [StreamEventSource] implementation for YouTube Live Chat via REST polling.
 *
 * Connects to the YouTube Data API v3, polls liveChatMessages at intervals
 * dictated by the API's `pollingIntervalMillis` response field (capped by
 * [YouTubeConfig.maxPollsPerMinute]), and emits [StreamEvent]s.
 *
 * @param config YouTube API configuration.
 * @param httpClient Optional Ktor HttpClient.
 */
public class YouTubeLiveChatSource(
    private val config: YouTubeConfig,
    private val httpClient: HttpClient = createDefaultYouTubeClient(),
) : StreamEventSource {

    private val json = Json { ignoreUnknownKeys = true }
    private val _events = MutableSharedFlow<StreamEvent>(extraBufferCapacity = EVENT_BUFFER)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private var pollingScope: CoroutineScope? = null
    private var pollingJob: Job? = null

    override val events: Flow<StreamEvent> = _events.asSharedFlow()
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override val platform: StreamPlatform = StreamPlatform.YOUTUBE

    override suspend fun connect() {
        disconnect()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        pollingScope = scope
        _connectionState.value = ConnectionState.CONNECTING
        pollingJob = scope.launch { startPolling() }
    }

    override suspend fun disconnect() {
        pollingScope?.cancel()
        pollingScope = null
        pollingJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun startPolling() {
        val chatId = resolveLiveChatId() ?: run {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        _connectionState.value = ConnectionState.CONNECTED
        var pageToken: String? = null
        while (true) {
            try {
                val result = pollMessages(chatId, pageToken)
                pageToken = result.nextPageToken
                emitEvents(result.items)
                val intervalMs = enforceMinInterval(result.pollingIntervalMillis)
                delay(intervalMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FaceLinkLogger.w(TAG, "Poll failed, will retry", e)
                handlePollError()
            }
        }
    }

    private suspend fun pollMessages(
        chatId: String,
        pageToken: String?,
    ): PollResult {
        val responseText = httpClient.get(LIVE_CHAT_MESSAGES_URL) {
            parameter("liveChatId", chatId)
            parameter("part", "snippet,authorDetails")
            parameter("key", config.apiKey)
            parameter("maxResults", MAX_RESULTS)
            pageToken?.let { parameter("pageToken", it) }
            config.accessToken?.let { header("Authorization", "Bearer $it") }
        }.bodyAsText()
        return parsePollResponse(responseText)
    }

    private fun parsePollResponse(responseText: String): PollResult {
        val response = json.parseToJsonElement(responseText).jsonObject
        val nextToken = response["nextPageToken"]?.jsonPrimitive?.content
        val intervalMs = response["pollingIntervalMillis"]
            ?.jsonPrimitive?.longOrNull ?: DEFAULT_POLL_INTERVAL_MS
        val items = response["items"]?.jsonArray?.mapNotNull { element ->
            element.jsonObject
        }.orEmpty()
        return PollResult(nextToken, intervalMs, items)
    }

    private fun emitEvents(items: List<JsonObject>) {
        for (item in items) {
            val timestampMs = extractTimestamp(item)
            val event = YouTubeEventMapper.map(item, timestampMs)
            if (event != null) {
                _events.tryEmit(event)
            }
        }
    }

    private suspend fun handlePollError() {
        _connectionState.value = ConnectionState.RECONNECTING
        delay(ERROR_RETRY_DELAY_MS)
        _connectionState.value = ConnectionState.CONNECTED
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolveLiveChatId(): String? {
        config.liveChatId?.let { return it }
        val token = config.accessToken ?: return null
        return try {
            fetchActiveBroadcastChatId(token)
        } catch (e: Exception) {
            FaceLinkLogger.w(TAG, "Failed to resolve live chat id", e)
            null
        }
    }

    private suspend fun fetchActiveBroadcastChatId(accessToken: String): String? {
        val responseText = httpClient.get(LIVE_BROADCASTS_URL) {
            parameter("part", "snippet")
            parameter("broadcastStatus", "active")
            parameter("key", config.apiKey)
            header("Authorization", "Bearer $accessToken")
        }.bodyAsText()
        return parseActiveBroadcastChatId(responseText)
    }

    private fun parseActiveBroadcastChatId(responseText: String): String? {
        val response = json.parseToJsonElement(responseText).jsonObject
        val items = response["items"]?.jsonArray ?: return null
        val firstItem = items.firstOrNull()?.jsonObject ?: return null
        return firstItem["snippet"]?.jsonObject
            ?.get("liveChatId")?.jsonPrimitive?.content
    }

    /**
     * Enforces the minimum polling interval based on [YouTubeConfig.maxPollsPerMinute].
     */
    internal fun enforceMinInterval(apiIntervalMs: Long): Long {
        val minIntervalMs = MS_PER_MINUTE / config.maxPollsPerMinute
        return maxOf(apiIntervalMs, minIntervalMs.toLong())
    }
}

private fun extractTimestamp(item: JsonObject): Long {
    val publishedAt = item["snippet"]?.jsonObject
        ?.get("publishedAt")?.jsonPrimitive?.content
    return publishedAt?.let { parseIso8601(it) } ?: FALLBACK_TIMESTAMP_MS
}

@Suppress("TooGenericExceptionCaught")
private fun parseIso8601(input: String): Long {
    return try {
        parseIso8601Parts(input)
    } catch (e: Exception) {
        FaceLinkLogger.w(TAG, "Failed to parse ISO-8601 timestamp '$input', using fallback", e)
        FALLBACK_TIMESTAMP_MS
    }
}

private fun parseIso8601Parts(input: String): Long {
    val cleaned = input.trimEnd('Z', 'z')
    val parts = cleaned.split('T')
    if (parts.size != DATE_TIME_PARTS) return FALLBACK_TIMESTAMP_MS
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

private fun createDefaultYouTubeClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

private data class PollResult(
    val nextPageToken: String?,
    val pollingIntervalMillis: Long,
    val items: List<JsonObject>,
)

private const val TAG = "YouTubeLiveChatSource"
private const val LIVE_CHAT_MESSAGES_URL =
    "https://www.googleapis.com/youtube/v3/liveChat/messages"
private const val LIVE_BROADCASTS_URL =
    "https://www.googleapis.com/youtube/v3/liveBroadcasts"
private const val EVENT_BUFFER = 64
private const val MAX_RESULTS = 200
private const val DEFAULT_POLL_INTERVAL_MS = 10_000L
private const val ERROR_RETRY_DELAY_MS = 30_000L
private const val MS_PER_MINUTE = 60_000
private const val FALLBACK_TIMESTAMP_MS = 0L
private const val DATE_TIME_PARTS = 2
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
