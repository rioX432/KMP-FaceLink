package io.github.kmpfacelink

import io.github.kmpfacelink.model.PowerMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PowerModeTest {

    @Test
    fun hasThreeEntries() {
        assertEquals(3, PowerMode.entries.size)
    }

    @Test
    fun highModeIs60Fps() {
        assertEquals(60, PowerMode.HIGH.baseFps)
    }

    @Test
    fun balancedModeIs30Fps() {
        assertEquals(30, PowerMode.BALANCED.baseFps)
    }

    @Test
    fun lowPowerModeIs15Fps() {
        assertEquals(15, PowerMode.LOW_POWER.baseFps)
    }

    @Test
    fun ordinalOrder() {
        assertEquals(0, PowerMode.HIGH.ordinal)
        assertEquals(1, PowerMode.BALANCED.ordinal)
        assertEquals(2, PowerMode.LOW_POWER.ordinal)
    }
}
