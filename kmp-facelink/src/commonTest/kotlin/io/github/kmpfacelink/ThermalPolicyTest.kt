package io.github.kmpfacelink

import io.github.kmpfacelink.model.ThermalPolicy
import io.github.kmpfacelink.model.ThermalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ThermalPolicyTest {

    @Test
    fun disabledIsSingleton() {
        val a = ThermalPolicy.Disabled
        val b = ThermalPolicy.Disabled
        assertTrue(a === b)
    }

    @Test
    fun adaptiveDefaultValues() {
        val policy = ThermalPolicy.Adaptive()
        assertEquals(25, policy.fairTargetFps)
        assertEquals(20, policy.seriousTargetFps)
        assertEquals(15, policy.criticalTargetFps)
        assertTrue(policy.disableEnhancerOnSerious)
    }

    @Test
    fun adaptiveCustomValues() {
        val policy = ThermalPolicy.Adaptive(
            fairTargetFps = 20,
            seriousTargetFps = 15,
            criticalTargetFps = 10,
            disableEnhancerOnSerious = false,
        )
        assertEquals(20, policy.fairTargetFps)
        assertEquals(15, policy.seriousTargetFps)
        assertEquals(10, policy.criticalTargetFps)
        assertFalse(policy.disableEnhancerOnSerious)
    }

    @Test
    fun adaptiveRejectsZeroFairTargetFps() {
        try {
            ThermalPolicy.Adaptive(fairTargetFps = 0)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("fairTargetFps"))
        }
    }

    @Test
    fun adaptiveRejectsNegativeSeriousTargetFps() {
        try {
            ThermalPolicy.Adaptive(seriousTargetFps = -1)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("seriousTargetFps"))
        }
    }

    @Test
    fun adaptiveRejectsZeroCriticalTargetFps() {
        try {
            ThermalPolicy.Adaptive(criticalTargetFps = 0)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("criticalTargetFps"))
        }
    }
}

class ThermalStateTest {

    @Test
    fun hasFourEntries() {
        assertEquals(4, ThermalState.entries.size)
    }

    @Test
    fun ordinalOrder() {
        assertTrue(ThermalState.NOMINAL.ordinal < ThermalState.FAIR.ordinal)
        assertTrue(ThermalState.FAIR.ordinal < ThermalState.SERIOUS.ordinal)
        assertTrue(ThermalState.SERIOUS.ordinal < ThermalState.CRITICAL.ordinal)
    }
}
