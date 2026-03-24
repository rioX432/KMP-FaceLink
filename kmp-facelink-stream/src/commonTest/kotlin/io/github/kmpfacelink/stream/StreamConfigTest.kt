package io.github.kmpfacelink.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StreamConfigTest {

    @Test
    fun defaultValues() {
        val config = StreamConfig()
        assertEquals(StreamConfig.DEFAULT_PORT, config.port)
        assertEquals(StreamConfig.DEFAULT_HOST, config.host)
        assertEquals(StreamConfig.DEFAULT_FPS, config.targetFps)
        assertEquals(StreamFormat.JSON, config.format)
    }

    @Test
    fun customValues() {
        val config = StreamConfig(
            host = "192.168.1.1",
            port = 8080,
            targetFps = 60,
            format = StreamFormat.BINARY,
        )
        assertEquals("192.168.1.1", config.host)
        assertEquals(8080, config.port)
        assertEquals(60, config.targetFps)
        assertEquals(StreamFormat.BINARY, config.format)
    }

    @Test
    fun fpsOutOfRangeThrows() {
        assertFailsWith<IllegalArgumentException> {
            StreamConfig(targetFps = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            StreamConfig(targetFps = 61)
        }
    }

    @Test
    fun portOutOfRangeThrows() {
        assertFailsWith<IllegalArgumentException> {
            StreamConfig(port = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            StreamConfig(port = 70000)
        }
    }
}
