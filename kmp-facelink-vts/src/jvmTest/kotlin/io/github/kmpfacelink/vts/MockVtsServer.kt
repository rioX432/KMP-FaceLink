package io.github.kmpfacelink.vts

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Mock VTube Studio WebSocket server for integration testing.
 *
 * Simulates the VTS Plugin API authentication and parameter injection flow.
 */
internal class MockVtsServer(
    private val port: Int,
    private val acceptAuth: Boolean = true,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var serverJob: Job? = null

    /** Number of InjectParameterDataRequest messages received. */
    var injectCount: Int = 0
        private set

    /** Last injected parameter values. */
    var lastInjectedParams: List<ParameterValue> = emptyList()
        private set

    fun start(scope: CoroutineScope) {
        serverJob = scope.launch { runServer() }
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
    }

    private suspend fun runServer() {
        embeddedServer(CIO, port = port) {
            install(WebSockets)
            routing {
                webSocket("/") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val response = handleMessage(frame.readText())
                            if (response != null) {
                                send(response)
                            }
                        }
                    }
                }
            }
        }.start(wait = true)
    }

    private fun handleMessage(text: String): String? {
        val envelope = json.decodeFromString<VtsEnvelope>(text)
        return when (envelope.messageType) {
            "APIStateRequest" -> buildResponse(envelope, "APIStateResponse") {
                put("active", true)
                put("vTubeStudioVersion", "1.9.0")
                put("currentSessionAuthenticated", false)
            }
            "AuthenticationTokenRequest" -> handleAuthTokenRequest(envelope)
            "AuthenticationRequest" -> handleAuthRequest(envelope)
            "InjectParameterDataRequest" -> handleInjectRequest(envelope)
            "ParameterCreationRequest" -> handleCreateParam(envelope)
            else -> null
        }
    }

    private fun handleAuthTokenRequest(envelope: VtsEnvelope): String =
        if (acceptAuth) {
            buildResponse(envelope, "AuthenticationTokenResponse") {
                put("authenticationToken", "mock-token-12345")
            }
        } else {
            buildErrorResponse(envelope, 50, "User has denied API access.")
        }

    private fun handleAuthRequest(envelope: VtsEnvelope): String {
        val token = envelope.data?.get("authenticationToken")
            ?.jsonPrimitive?.content
        return if (token != null && acceptAuth) {
            buildResponse(envelope, "AuthenticationResponse") {
                put("authenticated", true)
                put("reason", "Token valid.")
            }
        } else {
            buildResponse(envelope, "AuthenticationResponse") {
                put("authenticated", false)
                put("reason", "Invalid token.")
            }
        }
    }

    private fun handleInjectRequest(envelope: VtsEnvelope): String {
        injectCount++
        if (envelope.data != null) {
            val injectData: InjectParameterData = json.decodeFromJsonElement(
                InjectParameterData.serializer(),
                envelope.data,
            )
            lastInjectedParams = injectData.parameterValues
        }
        return buildResponse(envelope, "InjectParameterDataResponse") {}
    }

    private fun handleCreateParam(envelope: VtsEnvelope): String {
        val name = envelope.data?.get("parameterName")?.jsonPrimitive?.content ?: ""
        return buildResponse(envelope, "ParameterCreationResponse") {
            put("parameterName", name)
        }
    }

    private fun buildResponse(
        request: VtsEnvelope,
        messageType: String,
        dataBuilder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): String {
        val responseData = buildJsonObject(dataBuilder)
        val response = VtsEnvelope(
            messageType = messageType,
            requestID = request.requestID,
            timestamp = System.currentTimeMillis(),
            data = responseData,
        )
        return json.encodeToString(response)
    }

    private fun buildErrorResponse(
        request: VtsEnvelope,
        errorId: Int,
        message: String,
    ): String {
        val errorData = buildJsonObject {
            put("errorID", errorId)
            put("message", message)
        }
        val response = VtsEnvelope(
            messageType = "APIError",
            requestID = request.requestID,
            timestamp = System.currentTimeMillis(),
            data = errorData,
        )
        return json.encodeToString(response)
    }
}
