package io.github.kmpfacelink.vts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Parses VTube Studio API response JSON strings.
 */
internal object VtsResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** Parses the response envelope from a raw JSON string. */
    fun parseEnvelope(text: String): VtsEnvelope = json.decodeFromString(text)

    /** Returns true if the response is an API error. */
    fun isError(envelope: VtsEnvelope): Boolean = envelope.messageType == "APIError"

    /** Extracts the error data from an error response. */
    fun parseError(envelope: VtsEnvelope): VtsErrorData {
        requireNotNull(envelope.data) { "Error response has no data" }
        return json.decodeFromJsonElement(envelope.data)
    }

    /** Extracts auth token from AuthenticationTokenResponse. */
    fun parseAuthTokenResponse(data: JsonObject): AuthTokenResponseData =
        json.decodeFromJsonElement(data)

    /** Extracts auth result from AuthenticationResponse. */
    fun parseAuthResponse(data: JsonObject): AuthResponseData =
        json.decodeFromJsonElement(data)

    /** Extracts API state from APIStateResponse. */
    fun parseApiStateResponse(data: JsonObject): ApiStateResponseData =
        json.decodeFromJsonElement(data)
}
