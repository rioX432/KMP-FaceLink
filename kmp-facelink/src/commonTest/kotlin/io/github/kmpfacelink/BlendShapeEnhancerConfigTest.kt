package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeEnhancerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class BlendShapeEnhancerConfigTest {

    @Test
    fun noneIsSingleton() {
        val a = BlendShapeEnhancerConfig.None
        val b = BlendShapeEnhancerConfig.None
        assertTrue(a === b)
    }

    @Test
    fun defaultGeometricBlendWeightIsPointSeven() {
        val config = BlendShapeEnhancerConfig.Default()
        assertEquals(0.7f, config.geometricBlendWeight)
    }

    @Test
    fun defaultSensitivityMapContainsCheekPuff() {
        val config = BlendShapeEnhancerConfig.Default()
        val sensitivity = config.sensitivityOverrides[BlendShape.CHEEK_PUFF]
        assertNotNull(sensitivity)
        assertEquals(3.0f, sensitivity)
    }

    @Test
    fun defaultDeadZoneMapContainsCheekPuff() {
        val config = BlendShapeEnhancerConfig.Default()
        val deadZone = config.deadZoneOverrides[BlendShape.CHEEK_PUFF]
        assertNotNull(deadZone)
        assertEquals(0.03f, deadZone)
    }

    @Test
    fun defaultSensitivityMapHasExpectedEntries() {
        val config = BlendShapeEnhancerConfig.Default()
        assertEquals(20, config.sensitivityOverrides.size)
    }

    @Test
    fun defaultDeadZoneMapHasExpectedEntries() {
        val config = BlendShapeEnhancerConfig.Default()
        assertEquals(20, config.deadZoneOverrides.size)
    }

    @Test
    fun customGeometricBlendWeightAssigned() {
        val config = BlendShapeEnhancerConfig.Default(geometricBlendWeight = 0.5f)
        assertEquals(0.5f, config.geometricBlendWeight)
    }

    @Test
    fun geometricBlendWeightZeroIsValid() {
        val config = BlendShapeEnhancerConfig.Default(geometricBlendWeight = 0f)
        assertEquals(0f, config.geometricBlendWeight)
    }

    @Test
    fun geometricBlendWeightOneIsValid() {
        val config = BlendShapeEnhancerConfig.Default(geometricBlendWeight = 1f)
        assertEquals(1f, config.geometricBlendWeight)
    }

    @Test
    fun geometricBlendWeightAboveOneIsRejected() {
        try {
            BlendShapeEnhancerConfig.Default(geometricBlendWeight = 1.1f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("geometricBlendWeight"))
        }
    }

    @Test
    fun geometricBlendWeightBelowZeroIsRejected() {
        try {
            BlendShapeEnhancerConfig.Default(geometricBlendWeight = -0.1f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("geometricBlendWeight"))
        }
    }

    @Test
    fun companionDefaultSensitivityMapIsNotEmpty() {
        assertTrue(BlendShapeEnhancerConfig.defaultSensitivityMap.isNotEmpty())
    }

    @Test
    fun companionDefaultDeadZoneMapIsNotEmpty() {
        assertTrue(BlendShapeEnhancerConfig.defaultDeadZoneMap.isNotEmpty())
    }

    @Test
    fun eyeWideSensitivityIsCorrect() {
        val sensitivity = BlendShapeEnhancerConfig.defaultSensitivityMap[BlendShape.EYE_WIDE_LEFT]
        assertEquals(2.5f, sensitivity)
    }
}
