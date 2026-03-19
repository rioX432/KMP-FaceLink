package io.github.kmpfacelink

import io.github.kmpfacelink.model.ThermalPolicy
import io.github.kmpfacelink.model.ThermalState
import io.github.kmpfacelink.util.FrameThrottler
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameThrottlerTest {

    @Test
    fun disabledPolicyProcessesAllFrames() {
        val throttler = FrameThrottler(ThermalPolicy.Disabled)
        for (i in 0L..10L) {
            assertTrue(throttler.shouldProcess(i, ThermalState.CRITICAL))
        }
    }

    @Test
    fun nominalStateProcessesAllFrames() {
        val throttler = FrameThrottler(ThermalPolicy.Adaptive())
        for (i in 0L..10L) {
            assertTrue(throttler.shouldProcess(i, ThermalState.NOMINAL))
        }
    }

    @Test
    fun fairStateThrottlesFrames() {
        val throttler = FrameThrottler(ThermalPolicy.Adaptive(fairTargetFps = 25))
        // At 25fps, min interval = 40ms
        assertTrue(throttler.shouldProcess(0L, ThermalState.FAIR))
        assertFalse(throttler.shouldProcess(10L, ThermalState.FAIR))
        assertFalse(throttler.shouldProcess(39L, ThermalState.FAIR))
        assertTrue(throttler.shouldProcess(40L, ThermalState.FAIR))
    }

    @Test
    fun seriousStateThrottlesMoreAggressively() {
        val throttler = FrameThrottler(ThermalPolicy.Adaptive(seriousTargetFps = 20))
        // At 20fps, min interval = 50ms
        assertTrue(throttler.shouldProcess(0L, ThermalState.SERIOUS))
        assertFalse(throttler.shouldProcess(49L, ThermalState.SERIOUS))
        assertTrue(throttler.shouldProcess(50L, ThermalState.SERIOUS))
    }

    @Test
    fun criticalStateThrottlesMostAggressively() {
        val throttler = FrameThrottler(ThermalPolicy.Adaptive(criticalTargetFps = 15))
        // At 15fps, min interval = 66ms (1000/15 = 66)
        assertTrue(throttler.shouldProcess(0L, ThermalState.CRITICAL))
        assertFalse(throttler.shouldProcess(65L, ThermalState.CRITICAL))
        assertTrue(throttler.shouldProcess(66L, ThermalState.CRITICAL))
    }

    @Test
    fun shouldDisableEnhancerWhenDisabledPolicy() {
        val throttler = FrameThrottler(ThermalPolicy.Disabled)
        assertFalse(throttler.shouldDisableEnhancer(ThermalState.CRITICAL))
    }

    @Test
    fun shouldDisableEnhancerWhenFlagIsFalse() {
        val throttler = FrameThrottler(
            ThermalPolicy.Adaptive(disableEnhancerOnSerious = false),
        )
        assertFalse(throttler.shouldDisableEnhancer(ThermalState.SERIOUS))
        assertFalse(throttler.shouldDisableEnhancer(ThermalState.CRITICAL))
    }

    @Test
    fun shouldNotDisableEnhancerBelowSerious() {
        val throttler = FrameThrottler(ThermalPolicy.Adaptive())
        assertFalse(throttler.shouldDisableEnhancer(ThermalState.NOMINAL))
        assertFalse(throttler.shouldDisableEnhancer(ThermalState.FAIR))
    }

    @Test
    fun shouldDisableEnhancerAtSeriousAndAbove() {
        val throttler = FrameThrottler(ThermalPolicy.Adaptive())
        assertTrue(throttler.shouldDisableEnhancer(ThermalState.SERIOUS))
        assertTrue(throttler.shouldDisableEnhancer(ThermalState.CRITICAL))
    }
}
