package io.github.kmpfacelink.llm.internal

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val SSE_DATA_PREFIX = "data: "
private const val SSE_DONE_MARKER = "[DONE]"

/**
 * Parses an HTTP response as a Server-Sent Events (SSE) stream.
 *
 * Reads lines from the response body channel and emits only the `data:` payloads,
 * stopping when `[DONE]` is received or the channel closes.
 *
 * @return Flow of raw SSE data strings (JSON payloads)
 */
internal fun HttpResponse.sseDataFlow(): Flow<String> = flow {
    val channel = bodyAsChannel()
    var shouldStop = false
    while (!channel.isClosedForRead && !shouldStop) {
        val line = channel.readUTF8Line() ?: break
        if (line.startsWith(SSE_DATA_PREFIX)) {
            val data = line.removePrefix(SSE_DATA_PREFIX).trim()
            if (data == SSE_DONE_MARKER) {
                shouldStop = true
            } else if (data.isNotEmpty()) {
                emit(data)
            }
        }
    }
}
