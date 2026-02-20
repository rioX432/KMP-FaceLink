package io.github.kmpfacelink.stream.vts.protocol

import kotlinx.serialization.json.Json

internal object VtsSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encodeRequest(request: VtsRequest): String = json.encodeToString(request)

    fun decodeResponse(text: String): VtsResponse = json.decodeFromString(text)

    fun authTokenRequest(
        requestId: String,
        pluginName: String,
        pluginDeveloper: String,
        pluginIcon: String?,
    ): String = encodeRequest(
        VtsRequest(
            requestID = requestId,
            messageType = "AuthenticationTokenRequest",
            data = VtsRequestData(
                pluginName = pluginName,
                pluginDeveloper = pluginDeveloper,
                pluginIcon = pluginIcon,
            ),
        ),
    )

    fun authRequest(
        requestId: String,
        pluginName: String,
        pluginDeveloper: String,
        token: String,
    ): String = encodeRequest(
        VtsRequest(
            requestID = requestId,
            messageType = "AuthenticationRequest",
            data = VtsRequestData(
                pluginName = pluginName,
                pluginDeveloper = pluginDeveloper,
                authenticationToken = token,
            ),
        ),
    )

    fun injectParameterDataRequest(
        requestId: String,
        faceFound: Boolean,
        parameters: Map<String, Float>,
    ): String = encodeRequest(
        VtsRequest(
            requestID = requestId,
            messageType = "InjectParameterDataRequest",
            data = VtsRequestData(
                faceFound = faceFound,
                mode = "set",
                parameterValues = parameters.map { (id, value) ->
                    VtsParameterValue(id = id, value = value)
                },
            ),
        ),
    )
}
