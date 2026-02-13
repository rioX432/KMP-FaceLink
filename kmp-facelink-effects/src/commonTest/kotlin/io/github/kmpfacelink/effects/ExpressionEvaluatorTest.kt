package io.github.kmpfacelink.effects

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpressionEvaluatorTest {

    @Test
    fun singleBlendShapeAboveThreshold() {
        val data = emptyBlendShapeData().toMutableMap().apply {
            this[BlendShape.MOUTH_SMILE_LEFT] = HIGH_VALUE
        }
        val result = ExpressionEvaluator.evaluate(
            listOf(BlendShape.MOUTH_SMILE_LEFT),
            data,
            threshold = THRESHOLD_04,
            mapping = IntensityMapping.Linear,
        )
        assertEquals(HIGH_VALUE, result)
    }

    @Test
    fun singleBlendShapeBelowThreshold() {
        val data = emptyBlendShapeData().toMutableMap().apply {
            this[BlendShape.MOUTH_SMILE_LEFT] = LOW_VALUE
        }
        val result = ExpressionEvaluator.evaluate(
            listOf(BlendShape.MOUTH_SMILE_LEFT),
            data,
            threshold = THRESHOLD_04,
            mapping = IntensityMapping.Linear,
        )
        assertEquals(0f, result)
    }

    @Test
    fun multipleBlendShapesAveraged() {
        val data = emptyBlendShapeData().toMutableMap().apply {
            this[BlendShape.MOUTH_SMILE_LEFT] = HIGH_VALUE
            this[BlendShape.MOUTH_SMILE_RIGHT] = MID_VALUE
        }
        val result = ExpressionEvaluator.evaluate(
            listOf(BlendShape.MOUTH_SMILE_LEFT, BlendShape.MOUTH_SMILE_RIGHT),
            data,
            threshold = 0f,
            mapping = IntensityMapping.Linear,
        )
        assertEquals(EXPECTED_AVERAGE, result, FLOAT_TOLERANCE)
    }

    @Test
    fun emptyBlendShapeListReturnsZero() {
        val data = emptyBlendShapeData()
        val result = ExpressionEvaluator.evaluate(
            emptyList(),
            data,
            threshold = 0f,
            mapping = IntensityMapping.Linear,
        )
        assertEquals(0f, result)
    }

    @Test
    fun stepMappingApplied() {
        val data = emptyBlendShapeData().toMutableMap().apply {
            this[BlendShape.TONGUE_OUT] = HIGH_VALUE
        }
        val result = ExpressionEvaluator.evaluate(
            listOf(BlendShape.TONGUE_OUT),
            data,
            threshold = 0f,
            mapping = IntensityMapping.Step(THRESHOLD_06),
        )
        assertEquals(1f, result)
    }

    @Test
    fun curveMappingApplied() {
        val data = emptyBlendShapeData().toMutableMap().apply {
            this[BlendShape.TONGUE_OUT] = HIGH_VALUE
        }
        val result = ExpressionEvaluator.evaluate(
            listOf(BlendShape.TONGUE_OUT),
            data,
            threshold = 0f,
            mapping = IntensityMapping.Curve { it * it },
        )
        assertEquals(EXPECTED_SQUARED, result, FLOAT_TOLERANCE)
    }

    companion object {
        private const val HIGH_VALUE = 0.8f
        private const val MID_VALUE = 0.6f
        private const val LOW_VALUE = 0.1f
        private const val THRESHOLD_04 = 0.4f
        private const val THRESHOLD_06 = 0.6f
        private const val EXPECTED_AVERAGE = 0.7f
        private const val EXPECTED_SQUARED = 0.64f
        private const val FLOAT_TOLERANCE = 0.01f
    }
}
