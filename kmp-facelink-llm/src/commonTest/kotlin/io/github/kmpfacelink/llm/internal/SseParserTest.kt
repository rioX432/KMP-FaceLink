package io.github.kmpfacelink.llm.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SseParserTest {

    private fun buildSseBody(vararg lines: String): String = lines.joinToString("\n") + "\n"

    private suspend fun collectSse(body: String): List<String> {
        val results = mutableListOf<String>()
        val engine = MockEngine { _ ->
            respond(
                content = body,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
            )
        }
        HttpClient(engine).use { client ->
            val response = client.get("https://example.com/sse")
            response.sseDataFlow().collect { results.add(it) }
        }
        return results
    }

    @Test
    fun parsesStandardDataLines() = runTest {
        val body = buildSseBody(
            "data: {\"content\":\"Hello\"}",
            "data: {\"content\":\" world\"}",
            "data: [DONE]",
        )
        val results = collectSse(body)
        assertEquals(2, results.size)
        assertEquals("{\"content\":\"Hello\"}", results[0])
        assertEquals("{\"content\":\" world\"}", results[1])
    }

    @Test
    fun stopsOnDoneMarker() = runTest {
        val body = buildSseBody(
            "data: {\"chunk\":\"a\"}",
            "data: [DONE]",
            "data: {\"chunk\":\"b\"}",
        )
        val results = collectSse(body)
        assertEquals(1, results.size)
        assertEquals("{\"chunk\":\"a\"}", results[0])
    }

    @Test
    fun skipsLinesWithoutDataPrefix() = runTest {
        val body = buildSseBody(
            "event: start",
            "id: 42",
            "data: {\"text\":\"hi\"}",
            ": keep-alive comment",
            "data: [DONE]",
        )
        val results = collectSse(body)
        assertEquals(1, results.size)
        assertEquals("{\"text\":\"hi\"}", results[0])
    }

    @Test
    fun handlesEmptyDataPayload() = runTest {
        val body = buildSseBody(
            "data: ",
            "data: {\"text\":\"ok\"}",
            "data: [DONE]",
        )
        val results = collectSse(body)
        // Empty payload after trimming should be skipped
        assertEquals(1, results.size)
        assertEquals("{\"text\":\"ok\"}", results[0])
    }

    @Test
    fun ignoresKeepAliveEmptyLines() = runTest {
        val body = buildSseBody(
            "",
            "data: {\"chunk\":\"x\"}",
            "",
            "data: [DONE]",
        )
        val results = collectSse(body)
        assertEquals(1, results.size)
        assertEquals("{\"chunk\":\"x\"}", results[0])
    }

    @Test
    fun returnsEmptyListForOnlyDoneMarker() = runTest {
        val body = buildSseBody("data: [DONE]")
        val results = collectSse(body)
        assertTrue(results.isEmpty())
    }

    @Test
    fun returnsEmptyListForEmptyStream() = runTest {
        val results = collectSse("")
        assertTrue(results.isEmpty())
    }
}
