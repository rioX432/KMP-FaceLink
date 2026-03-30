package io.github.kmpfacelink.vts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VtsMessageBuilderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun apiStateRequestHasCorrectStructure() {
        val request = VtsMessageBuilder.buildApiStateRequest("test-1")
        val envelope = json.decodeFromString<VtsEnvelope>(request)
        assertEquals(API_NAME, envelope.apiName)
        assertEquals(API_VERSION, envelope.apiVersion)
        assertEquals("APIStateRequest", envelope.messageType)
        assertEquals("test-1", envelope.requestID)
    }

    @Test
    fun authTokenRequestContainsPluginInfo() {
        val request = VtsMessageBuilder.buildAuthTokenRequest(
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            pluginIcon = "base64data",
            requestId = "auth-1",
        )
        val envelope = json.decodeFromString<VtsEnvelope>(request)
        assertEquals("AuthenticationTokenRequest", envelope.messageType)
        val data = envelope.data!!
        assertEquals("TestPlugin", data["pluginName"]?.jsonPrimitive?.content)
        assertEquals("TestDev", data["pluginDeveloper"]?.jsonPrimitive?.content)
        assertEquals("base64data", data["pluginIcon"]?.jsonPrimitive?.content)
    }

    @Test
    fun authRequestContainsToken() {
        val request = VtsMessageBuilder.buildAuthRequest(
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            authToken = "my-token-123",
            requestId = "auth-2",
        )
        val envelope = json.decodeFromString<VtsEnvelope>(request)
        assertEquals("AuthenticationRequest", envelope.messageType)
        val data = envelope.data!!
        assertEquals("my-token-123", data["authenticationToken"]?.jsonPrimitive?.content)
    }

    @Test
    fun injectRequestContainsParameters() {
        val params = listOf(
            ParameterValue(id = "FaceAngleX", value = 15.5f),
            ParameterValue(id = "MouthOpen", value = 0.8f, weight = 0.5f),
        )
        val request = VtsMessageBuilder.buildInjectRequest(
            parameters = params,
            faceFound = true,
            mode = "set",
            requestId = "inject-1",
        )
        val envelope = json.decodeFromString<VtsEnvelope>(request)
        assertEquals("InjectParameterDataRequest", envelope.messageType)
        val data = envelope.data!!
        assertTrue(data.containsKey("parameterValues"))
        assertTrue(data.containsKey("faceFound"))
        assertTrue(data.containsKey("mode"))
    }

    @Test
    fun postProcessingListRequestHasCorrectStructure() {
        val request = VtsMessageBuilder.buildPostProcessingListRequest(
            fillPresets = true,
            fillEffects = true,
            effectIdFilter = listOf("ChromaticAberration"),
            requestId = "pp-1",
        )
        val envelope = json.decodeFromString<VtsEnvelope>(request)
        assertEquals("PostProcessingListRequest", envelope.messageType)
        assertEquals("pp-1", envelope.requestID)
        val data = envelope.data!!
        assertTrue(data.containsKey("fillPostProcessingPresetsArray"))
        assertTrue(data.containsKey("fillPostProcessingEffectsArray"))
        assertTrue(data.containsKey("effectIDFilter"))
    }

    @Test
    fun postProcessingUpdateRequestHasCorrectStructure() {
        val request = VtsMessageBuilder.buildPostProcessingUpdateRequest(
            values = listOf(
                PostProcessingValueData("Backlight_Strength", "0.8"),
            ),
            postProcessingOn = true,
            fadeTime = 0.5,
            requestId = "pp-2",
        )
        val envelope = json.decodeFromString<VtsEnvelope>(request)
        assertEquals("SetPostProcessingEffectValues", envelope.messageType)
        val data = envelope.data!!
        assertTrue(data.containsKey("postProcessingOn"))
        assertTrue(data.containsKey("postProcessingValues"))
        assertTrue(data.containsKey("postProcessingFadeTime"))
    }

    @Test
    fun parameterCreationRequestHasCorrectFields() {
        val request = VtsMessageBuilder.buildParameterCreationRequest(
            data = ParameterCreationData(
                parameterName = "CustomParam",
                explanation = "A test parameter",
                min = -10f,
                max = 10f,
                defaultValue = 0f,
            ),
            requestId = "create-1",
        )
        val envelope = json.decodeFromString<VtsEnvelope>(request)
        assertEquals("ParameterCreationRequest", envelope.messageType)
        val data = envelope.data!!
        assertEquals("CustomParam", data["parameterName"]?.jsonPrimitive?.content)
    }
}
