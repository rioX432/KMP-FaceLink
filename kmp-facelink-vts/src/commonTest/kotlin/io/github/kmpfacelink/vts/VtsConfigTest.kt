package io.github.kmpfacelink.vts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VtsConfigTest {

    @Test
    fun defaultValues() {
        val config = VtsConfig()
        assertEquals(VtsConfig.DEFAULT_HOST, config.host)
        assertEquals(VtsConfig.DEFAULT_PORT, config.port)
        assertEquals(VtsConfig.DEFAULT_PLUGIN_NAME, config.pluginName)
        assertEquals(VtsConfig.DEFAULT_PLUGIN_DEVELOPER, config.pluginDeveloper)
    }

    @Test
    fun customValues() {
        val config = VtsConfig(
            host = "192.168.1.1",
            port = 9001,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            authToken = "some-token",
        )
        assertEquals("192.168.1.1", config.host)
        assertEquals(9001, config.port)
        assertEquals("TestPlugin", config.pluginName)
        assertEquals("some-token", config.authToken)
    }

    @Test
    fun portOutOfRangeThrows() {
        assertFailsWith<IllegalArgumentException> {
            VtsConfig(port = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            VtsConfig(port = 70000)
        }
    }

    @Test
    fun pluginNameTooShortThrows() {
        assertFailsWith<IllegalArgumentException> {
            VtsConfig(pluginName = "AB")
        }
    }

    @Test
    fun pluginNameTooLongThrows() {
        assertFailsWith<IllegalArgumentException> {
            VtsConfig(pluginName = "A".repeat(33))
        }
    }

    @Test
    fun pluginDeveloperTooShortThrows() {
        assertFailsWith<IllegalArgumentException> {
            VtsConfig(pluginDeveloper = "AB")
        }
    }
}
