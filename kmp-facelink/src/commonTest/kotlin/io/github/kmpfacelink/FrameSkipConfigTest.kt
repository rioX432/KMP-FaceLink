package io.github.kmpfacelink

import io.github.kmpfacelink.model.FrameSkipConfig
import io.github.kmpfacelink.model.FrameSkipLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FrameSkipConfigTest {

    @Test
    fun defaultConfigIsDisabled() {
        val config = FrameSkipConfig()
        assertFalse(config.enabled)
        assertEquals(FrameSkipLevel.BALANCED, config.level)
    }

    @Test
    fun conservativeLevelValues() {
        val level = FrameSkipLevel.CONSERVATIVE
        assertEquals(0.9f, level.confidenceThreshold)
        assertEquals(1, level.maxConsecutiveSkips)
        assertEquals(0.015f, level.movementThreshold)
    }

    @Test
    fun balancedLevelValues() {
        val level = FrameSkipLevel.BALANCED
        assertEquals(0.8f, level.confidenceThreshold)
        assertEquals(2, level.maxConsecutiveSkips)
        assertEquals(0.025f, level.movementThreshold)
    }

    @Test
    fun aggressiveLevelValues() {
        val level = FrameSkipLevel.AGGRESSIVE
        assertEquals(0.7f, level.confidenceThreshold)
        assertEquals(3, level.maxConsecutiveSkips)
        assertEquals(0.04f, level.movementThreshold)
    }
}
