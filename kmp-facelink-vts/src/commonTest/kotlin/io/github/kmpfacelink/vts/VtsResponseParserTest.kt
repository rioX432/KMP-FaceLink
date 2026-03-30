package io.github.kmpfacelink.vts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VtsResponseParserTest {

    @Test
    fun parseApiStateResponse() {
        val responseJson = """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 1625405710728,
                "requestID": "test-1",
                "messageType": "APIStateResponse",
                "data": {
                    "active": true,
                    "vTubeStudioVersion": "1.9.0",
                    "currentSessionAuthenticated": false
                }
            }
        """.trimIndent()
        val envelope = VtsResponseParser.parseEnvelope(responseJson)
        assertEquals("APIStateResponse", envelope.messageType)
        assertEquals("test-1", envelope.requestID)
        assertFalse(VtsResponseParser.isError(envelope))

        val state = VtsResponseParser.parseApiStateResponse(envelope.data!!)
        assertTrue(state.active)
        assertEquals("1.9.0", state.vTubeStudioVersion)
        assertFalse(state.currentSessionAuthenticated)
    }

    @Test
    fun parseAuthTokenResponse() {
        val responseJson = """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 1625405710728,
                "requestID": "auth-1",
                "messageType": "AuthenticationTokenResponse",
                "data": {
                    "authenticationToken": "test-token-abc-123"
                }
            }
        """.trimIndent()
        val envelope = VtsResponseParser.parseEnvelope(responseJson)
        val data = VtsResponseParser.parseAuthTokenResponse(envelope.data!!)
        assertEquals("test-token-abc-123", data.authenticationToken)
    }

    @Test
    fun parseAuthResponse() {
        val responseJson = """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 1625405710728,
                "requestID": "auth-2",
                "messageType": "AuthenticationResponse",
                "data": {
                    "authenticated": true,
                    "reason": "Token valid."
                }
            }
        """.trimIndent()
        val envelope = VtsResponseParser.parseEnvelope(responseJson)
        val data = VtsResponseParser.parseAuthResponse(envelope.data!!)
        assertTrue(data.authenticated)
    }

    @Test
    fun parseErrorResponse() {
        val responseJson = """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 1625405710728,
                "requestID": "err-1",
                "messageType": "APIError",
                "data": {
                    "errorID": 50,
                    "message": "User has denied API access."
                }
            }
        """.trimIndent()
        val envelope = VtsResponseParser.parseEnvelope(responseJson)
        assertTrue(VtsResponseParser.isError(envelope))
        val error = VtsResponseParser.parseError(envelope)
        assertEquals(50, error.errorId)
        assertEquals("User has denied API access.", error.message)
    }

    @Test
    fun parsePostProcessingListResponse() {
        val responseJson = """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 1625405710728,
                "requestID": "pp-1",
                "messageType": "PostProcessingListResponse",
                "data": {
                    "postProcessingSupported": true,
                    "postProcessingActive": true,
                    "presetCount": 2,
                    "activePreset": "MyPreset",
                    "postProcessingEffects": [
                        {
                            "internalID": "bloom_001",
                            "enumID": "Bloom",
                            "explanation": "Adds glow effect",
                            "isActive": true,
                            "configEntries": [
                                {
                                    "internalID": "Bloom_Strength",
                                    "explanation": "Bloom intensity",
                                    "type": "Float",
                                    "activationConfig": false,
                                    "currentValue": "0.5",
                                    "minValue": "0",
                                    "maxValue": "1",
                                    "defaultValue": "0.3"
                                }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()
        val envelope = VtsResponseParser.parseEnvelope(responseJson)
        assertEquals("PostProcessingListResponse", envelope.messageType)
        assertFalse(VtsResponseParser.isError(envelope))

        val data = VtsResponseParser.parsePostProcessingListResponse(envelope.data!!)
        assertTrue(data.postProcessingSupported)
        assertTrue(data.postProcessingActive)
        assertEquals("MyPreset", data.activePreset)
        assertEquals(1, data.postProcessingEffects.size)

        val effect = data.postProcessingEffects[0]
        assertEquals("bloom_001", effect.internalID)
        assertEquals("Bloom", effect.enumID)
        assertTrue(effect.isActive)
        assertEquals(1, effect.configEntries.size)
        assertEquals("Bloom_Strength", effect.configEntries[0].internalID)
        assertEquals("0.5", effect.configEntries[0].currentValue)
    }

    @Test
    fun parseUnknownFieldsIgnored() {
        val responseJson = """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 1625405710728,
                "requestID": "test",
                "messageType": "AuthenticationResponse",
                "futureField": "ignored",
                "data": {
                    "authenticated": true,
                    "reason": "OK",
                    "extraField": 42
                }
            }
        """.trimIndent()
        val envelope = VtsResponseParser.parseEnvelope(responseJson)
        val data = VtsResponseParser.parseAuthResponse(envelope.data!!)
        assertTrue(data.authenticated)
    }
}
