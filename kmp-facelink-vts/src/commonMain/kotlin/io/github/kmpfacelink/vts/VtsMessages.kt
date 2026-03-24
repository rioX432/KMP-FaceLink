package io.github.kmpfacelink.vts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base envelope for all VTube Studio API messages.
 *
 * Every request and response uses this structure with the `data` field
 * varying by [messageType].
 */
@Serializable
internal data class VtsEnvelope(
    val apiName: String = API_NAME,
    val apiVersion: String = API_VERSION,
    val requestID: String = "",
    val messageType: String,
    val timestamp: Long = 0L,
    val data: kotlinx.serialization.json.JsonObject? = null,
)

internal const val API_NAME = "VTubeStudioPublicAPI"
internal const val API_VERSION = "1.0"

// -- Authentication Token Request/Response --

@Serializable
internal data class AuthTokenRequestData(
    val pluginName: String,
    val pluginDeveloper: String,
    val pluginIcon: String? = null,
)

@Serializable
internal data class AuthTokenResponseData(
    val authenticationToken: String,
)

// -- Authentication Request/Response --

@Serializable
internal data class AuthRequestData(
    val pluginName: String,
    val pluginDeveloper: String,
    val authenticationToken: String,
)

@Serializable
internal data class AuthResponseData(
    val authenticated: Boolean,
    val reason: String = "",
)

// -- API State --

@Serializable
internal data class ApiStateResponseData(
    val active: Boolean,
    val vTubeStudioVersion: String = "",
    val currentSessionAuthenticated: Boolean = false,
)

// -- Parameter Injection --

@Serializable
internal data class InjectParameterData(
    val faceFound: Boolean = true,
    val mode: String = "set",
    val parameterValues: List<ParameterValue>,
)

/**
 * A single parameter value for injection into VTube Studio.
 *
 * @property id VTS parameter name (e.g., "FaceAngleX", "MouthOpen")
 * @property value Parameter value (typically -1000000 to 1000000, commonly 0-1 for blendshapes)
 * @property weight Blending weight between 0 and 1 (mixes API value with VTS tracking)
 */
@Serializable
public data class ParameterValue(
    val id: String,
    val value: Float,
    val weight: Float = 1f,
)

// -- Parameter Creation --

@Serializable
internal data class ParameterCreationData(
    val parameterName: String,
    val explanation: String = "",
    val min: Float = 0f,
    val max: Float = 1f,
    val defaultValue: Float = 0f,
)

@Serializable
internal data class ParameterCreationResponseData(
    val parameterName: String,
)

// -- Error --

@Serializable
internal data class VtsErrorData(
    @SerialName("errorID") val errorId: Int,
    val message: String,
)
