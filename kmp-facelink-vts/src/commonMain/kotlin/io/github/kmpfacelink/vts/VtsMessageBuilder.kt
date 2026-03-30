package io.github.kmpfacelink.vts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Builds VTube Studio API request JSON strings.
 *
 * Each method produces a complete JSON message ready to send over WebSocket.
 */
internal object VtsMessageBuilder {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** Builds an APIStateRequest message. */
    fun buildApiStateRequest(requestId: String = ""): String =
        buildRequest("APIStateRequest", requestId)

    /** Builds an AuthenticationTokenRequest message. */
    fun buildAuthTokenRequest(
        pluginName: String,
        pluginDeveloper: String,
        pluginIcon: String? = null,
        requestId: String = "",
    ): String {
        val data = AuthTokenRequestData(
            pluginName = pluginName,
            pluginDeveloper = pluginDeveloper,
            pluginIcon = pluginIcon,
        )
        return buildRequest("AuthenticationTokenRequest", requestId, encodeData(data))
    }

    /** Builds an AuthenticationRequest message. */
    fun buildAuthRequest(
        pluginName: String,
        pluginDeveloper: String,
        authToken: String,
        requestId: String = "",
    ): String {
        val data = AuthRequestData(
            pluginName = pluginName,
            pluginDeveloper = pluginDeveloper,
            authenticationToken = authToken,
        )
        return buildRequest("AuthenticationRequest", requestId, encodeData(data))
    }

    /** Builds an InjectParameterDataRequest message. */
    fun buildInjectRequest(
        parameters: List<ParameterValue>,
        faceFound: Boolean = true,
        mode: String = "set",
        requestId: String = "",
    ): String {
        val data = InjectParameterData(
            faceFound = faceFound,
            mode = mode,
            parameterValues = parameters,
        )
        return buildRequest("InjectParameterDataRequest", requestId, encodeData(data))
    }

    /** Builds a ParameterCreationRequest message. */
    fun buildParameterCreationRequest(
        data: ParameterCreationData,
        requestId: String = "",
    ): String = buildRequest("ParameterCreationRequest", requestId, encodeData(data))

    /** Builds a PostProcessingListRequest message. */
    fun buildPostProcessingListRequest(
        fillPresets: Boolean = false,
        fillEffects: Boolean = true,
        effectIdFilter: List<String> = emptyList(),
        requestId: String = "",
    ): String {
        val data = PostProcessingListRequestData(
            fillPostProcessingPresetsArray = fillPresets,
            fillPostProcessingEffectsArray = fillEffects,
            effectIDFilter = effectIdFilter,
        )
        return buildRequest("PostProcessingListRequest", requestId, encodeData(data))
    }

    /** Builds a SetPostProcessingEffectValues request message. */
    fun buildPostProcessingUpdateRequest(
        values: List<PostProcessingValueData>,
        postProcessingOn: Boolean = true,
        fadeTime: Double = 0.0,
        setAllOtherToDefault: Boolean = false,
        requestId: String = "",
    ): String {
        val data = PostProcessingUpdateRequestData(
            postProcessingOn = postProcessingOn,
            setPostProcessingValues = true,
            postProcessingFadeTime = fadeTime,
            setAllOtherValuesToDefault = setAllOtherToDefault,
            postProcessingValues = values,
        )
        return buildRequest("SetPostProcessingEffectValues", requestId, encodeData(data))
    }

    private fun buildRequest(
        messageType: String,
        requestId: String,
        data: JsonObject? = null,
    ): String {
        val envelope = VtsEnvelope(
            messageType = messageType,
            requestID = requestId,
            data = data,
        )
        return json.encodeToString(envelope)
    }

    private inline fun <reified T> encodeData(data: T): JsonObject =
        json.encodeToJsonElement(data).jsonObject
}
