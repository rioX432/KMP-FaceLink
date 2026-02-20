package io.github.kmpfacelink.stream.vts.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val VTS_API_NAME = "VTubeStudioPublicAPI"
internal const val VTS_API_VERSION = "1.0"

// -- Base envelope --

@Serializable
internal data class VtsRequest(
    val apiName: String = VTS_API_NAME,
    val apiVersion: String = VTS_API_VERSION,
    val requestID: String,
    val messageType: String,
    val data: VtsRequestData,
)

@Serializable
internal data class VtsResponse(
    val apiName: String = VTS_API_NAME,
    val apiVersion: String = VTS_API_VERSION,
    val timestamp: Long = 0,
    val requestID: String = "",
    val messageType: String,
    val data: VtsResponseData? = null,
)

// -- Request data variants (use polymorphism manually via messageType) --

@Serializable
internal data class VtsRequestData(
    // AuthenticationTokenRequest
    val pluginName: String? = null,
    val pluginDeveloper: String? = null,
    val pluginIcon: String? = null,
    // AuthenticationRequest
    val authenticationToken: String? = null,
    // InjectParameterDataRequest
    val faceFound: Boolean? = null,
    val mode: String? = null,
    val parameterValues: List<VtsParameterValue>? = null,
)

// -- Response data (union of all response fields) --

@Serializable
internal data class VtsResponseData(
    // AuthenticationTokenResponse
    val authenticationToken: String? = null,
    // AuthenticationResponse
    val authenticated: Boolean? = null,
    val reason: String? = null,
    // APIError
    @SerialName("errorID")
    val errorId: Int? = null,
    val message: String? = null,
)

// -- Shared types --

@Serializable
internal data class VtsParameterValue(
    val id: String,
    val value: Float,
    val weight: Float? = null,
)
