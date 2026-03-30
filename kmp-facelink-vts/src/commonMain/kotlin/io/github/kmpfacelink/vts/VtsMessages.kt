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

// -- Post-Processing List --

@Serializable
internal data class PostProcessingListRequestData(
    val fillPostProcessingPresetsArray: Boolean = false,
    val fillPostProcessingEffectsArray: Boolean = true,
    val effectIDFilter: List<String> = emptyList(),
)

@Serializable
internal data class PostProcessingListResponseData(
    val postProcessingSupported: Boolean = true,
    val postProcessingActive: Boolean = false,
    val presetCount: Int = 0,
    val activePreset: String = "",
    val postProcessingPresets: List<PostProcessingPresetData> = emptyList(),
    val postProcessingEffects: List<PostProcessingEffectData> = emptyList(),
)

@Serializable
internal data class PostProcessingPresetData(
    val id: String,
    val name: String,
    val index: Int = 0,
    val isActive: Boolean = false,
)

@Serializable
internal data class PostProcessingEffectData(
    val internalID: String,
    val enumID: String = "",
    val explanation: String = "",
    val isActive: Boolean = false,
    val configEntries: List<PostProcessingConfigEntryData> = emptyList(),
)

@Serializable
internal data class PostProcessingConfigEntryData(
    val internalID: String,
    val explanation: String = "",
    val type: String = "",
    val activationConfig: Boolean = false,
    val currentValue: String = "",
    val minValue: String = "",
    val maxValue: String = "",
    val defaultValue: String = "",
)

// -- Post-Processing Update --

@Serializable
internal data class PostProcessingUpdateRequestData(
    val postProcessingOn: Boolean = true,
    val setPostProcessingPreset: Boolean = false,
    val setPostProcessingValues: Boolean = true,
    val presetToSet: String = "",
    val postProcessingFadeTime: Double = 0.0,
    val setAllOtherValuesToDefault: Boolean = false,
    val usingRestrictedEffects: Boolean = false,
    val randomizeAll: Boolean = false,
    val randomizeAllChaosLevel: Double = 0.5,
    val postProcessingValues: List<PostProcessingValueData> = emptyList(),
)

@Serializable
internal data class PostProcessingValueData(
    val configId: String,
    val configValue: String,
)

// -- Error --

@Serializable
internal data class VtsErrorData(
    @SerialName("errorID") val errorId: Int,
    val message: String,
)
