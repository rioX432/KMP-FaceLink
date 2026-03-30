package io.github.kmpfacelink.avatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VrcOscParameterBuilderTest {

    @Test
    fun oscAddressPrefixesCorrectly() {
        val address = VrcOscParameterBuilder.oscAddress("v2/JawOpen")
        assertEquals("/avatar/parameters/v2/JawOpen", address)
    }

    @Test
    fun oscAddressWithRawName() {
        val address = VrcOscParameterBuilder.oscAddress("JawOpen")
        assertEquals("/avatar/parameters/JawOpen", address)
    }

    @Test
    fun buildOscEntriesCreatesCorrectPairs() {
        val params = mapOf(
            "v2/JawOpen" to 0.8f,
            "v2/EyeClosedLeft" to 0.5f,
        )
        val entries = VrcOscParameterBuilder.buildOscEntries(params)
        assertEquals(2, entries.size)
        assertEquals("/avatar/parameters/v2/JawOpen" to 0.8f, entries[0])
        assertEquals("/avatar/parameters/v2/EyeClosedLeft" to 0.5f, entries[1])
    }

    @Test
    fun emptyParametersProducesEmptyEntries() {
        val entries = VrcOscParameterBuilder.buildOscEntries(emptyMap())
        assertTrue(entries.isEmpty())
    }

    @Test
    fun avatarParameterPrefixConstant() {
        assertEquals("/avatar/parameters/", VrcOscParameterBuilder.AVATAR_PARAMETER_PREFIX)
    }
}
