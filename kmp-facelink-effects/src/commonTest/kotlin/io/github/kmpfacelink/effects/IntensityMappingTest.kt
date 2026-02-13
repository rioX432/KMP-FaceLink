package io.github.kmpfacelink.effects

import kotlin.test.Test
import kotlin.test.assertEquals

class IntensityMappingTest {

    @Test
    fun linearPassesThrough() {
        assertEquals(VALUE_05, IntensityMapping.Linear.map(VALUE_05))
        assertEquals(0f, IntensityMapping.Linear.map(0f))
        assertEquals(1f, IntensityMapping.Linear.map(1f))
    }

    @Test
    fun stepAboveThreshold() {
        assertEquals(1f, IntensityMapping.Step(VALUE_05).map(VALUE_08))
    }

    @Test
    fun stepAtThreshold() {
        assertEquals(1f, IntensityMapping.Step(VALUE_05).map(VALUE_05))
    }

    @Test
    fun stepBelowThreshold() {
        assertEquals(0f, IntensityMapping.Step(VALUE_05).map(VALUE_03))
    }

    @Test
    fun curveAppliesFunction() {
        val curve = IntensityMapping.Curve { it * it }
        assertEquals(EXPECTED_SQUARED, curve.map(VALUE_05), FLOAT_TOLERANCE)
    }

    @Test
    fun curveClampsToRange() {
        val curve = IntensityMapping.Curve { it * SCALE_FACTOR }
        assertEquals(1f, curve.map(VALUE_08))
    }

    @Test
    fun curveClampsNegative() {
        val curve = IntensityMapping.Curve { -1f }
        assertEquals(0f, curve.map(VALUE_05))
    }

    companion object {
        private const val VALUE_03 = 0.3f
        private const val VALUE_05 = 0.5f
        private const val VALUE_08 = 0.8f
        private const val EXPECTED_SQUARED = 0.25f
        private const val SCALE_FACTOR = 2f
        private const val FLOAT_TOLERANCE = 0.001f
    }
}
