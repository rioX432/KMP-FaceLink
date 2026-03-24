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
