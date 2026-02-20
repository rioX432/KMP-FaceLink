package io.github.kmpfacelink.stream.vts

import io.github.kmpfacelink.stream.vts.protocol.VtsSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VtsSerializerTest {

    @Test
    fun authTokenRequestContainsRequiredFields() {
        val json = VtsSerializer.authTokenRequest(
            requestId = "test-1",
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            pluginIcon = null,
        )
        assertTrue(json.contains("\"messageType\":\"AuthenticationTokenRequest\""))
        assertTrue(json.contains("\"pluginName\":\"TestPlugin\""))
        assertTrue(json.contains("\"pluginDeveloper\":\"TestDev\""))
        assertTrue(json.contains("\"apiName\":\"VTubeStudioPublicAPI\""))
        assertTrue(json.contains("\"apiVersion\":\"1.0\""))
    }

    @Test
    fun authRequestContainsToken() {
        val json = VtsSerializer.authRequest(
            requestId = "test-2",
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            token = "my-token-123",
        )
        assertTrue(json.contains("\"messageType\":\"AuthenticationRequest\""))
        assertTrue(json.contains("\"authenticationToken\":\"my-token-123\""))
    }

    @Test
    fun injectParameterDataRequestFormat() {
        val params = mapOf("eyeBlinkLeft" to 0.5f, "FaceAngleX" to 10.0f)
        val json = VtsSerializer.injectParameterDataRequest(
            requestId = "test-3",
            faceFound = true,
            parameters = params,
        )
        assertTrue(json.contains("\"messageType\":\"InjectParameterDataRequest\""))
        assertTrue(json.contains("\"faceFound\":true"))
        assertTrue(json.contains("\"mode\":\"set\""))
        assertTrue(json.contains("\"id\":\"eyeBlinkLeft\""))
        assertTrue(json.contains("\"id\":\"FaceAngleX\""))
    }

    @Test
    fun decodeAuthTokenResponse() {
        val responseJson = """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 1625405710728,
                "requestID": "test-1",
                "messageType": "AuthenticationTokenResponse",
                "data": {
                    "authenticationToken": "token-abc-123"
                }
            }
        """.trimIndent()

        val response = VtsSerializer.decodeResponse(responseJson)
        assertEquals("AuthenticationTokenResponse", response.messageType)
        assertNotNull(response.data)
        assertEquals("token-abc-123", response.data?.authenticationToken)
    }

    @Test
    fun decodeAuthResponse() {
        val responseJson = """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 1625405710728,
                "requestID": "test-2",
                "messageType": "AuthenticationResponse",
                "data": {
                    "authenticated": true,
                    "reason": "Token valid."
                }
            }
        """.trimIndent()

        val response = VtsSerializer.decodeResponse(responseJson)
        assertEquals("AuthenticationResponse", response.messageType)
        assertEquals(true, response.data?.authenticated)
    }

    @Test
    fun decodeApiError() {
        val responseJson = """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 1625405710728,
                "requestID": "test-3",
                "messageType": "APIError",
                "data": {
                    "errorID": 50,
                    "message": "User has denied API access."
                }
            }
        """.trimIndent()

        val response = VtsSerializer.decodeResponse(responseJson)
        assertEquals("APIError", response.messageType)
        assertEquals(50, response.data?.errorId)
        assertEquals("User has denied API access.", response.data?.message)
    }

    @Test
    fun decodeResponseIgnoresUnknownFields() {
        val responseJson = """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 1625405710728,
                "requestID": "test-4",
                "messageType": "InjectParameterDataResponse",
                "data": {
                    "someUnknownField": "value"
                }
            }
        """.trimIndent()

        val response = VtsSerializer.decodeResponse(responseJson)
        assertEquals("InjectParameterDataResponse", response.messageType)
    }
}
