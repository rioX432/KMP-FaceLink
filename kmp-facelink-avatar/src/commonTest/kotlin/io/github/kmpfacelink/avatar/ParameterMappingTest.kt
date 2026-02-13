package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.valueOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ParameterMappingTest {

    private fun trackingData(
        blendShapes: Map<BlendShape, Float> = emptyMap(),
    ) = FaceTrackingData(
        blendShapes = blendShapes,
        headTransform = HeadTransform(),
        timestampMs = 0L,
        isTracking = true,
    )

    @Test
    fun directPassesThrough() {
        val mapping = ParameterMapping.Direct(BlendShape.JAW_OPEN)
        val data = trackingData(mapOf(BlendShape.JAW_OPEN to 0.75f))
        val result = data.blendShapes.valueOf(mapping.blendShape)
        assertEquals(0.75f, result, TOLERANCE)
    }

    @Test
    fun invertedReturnsOneMinus() {
        val mapping = ParameterMapping.Inverted(BlendShape.EYE_BLINK_LEFT)
        val data = trackingData(mapOf(BlendShape.EYE_BLINK_LEFT to 0.3f))
        val result = 1f - data.blendShapes.valueOf(mapping.blendShape)
        assertEquals(0.7f, result, TOLERANCE)
    }

    @Test
    fun scaledAppliesScaleAndOffset() {
        val mapping = ParameterMapping.Scaled(
            blendShape = BlendShape.JAW_OPEN,
            scale = 2f,
            offset = -0.5f,
            min = 0f,
            max = 1f,
        )
        val data = trackingData(mapOf(BlendShape.JAW_OPEN to 0.6f))
        val raw = data.blendShapes.valueOf(mapping.blendShape)
        val result = (raw * mapping.scale + mapping.offset).coerceIn(mapping.min, mapping.max)
        // 0.6 * 2 + (-0.5) = 0.7
        assertEquals(0.7f, result, TOLERANCE)
    }

    @Test
    fun scaledClampsToRange() {
        val mapping = ParameterMapping.Scaled(
            blendShape = BlendShape.JAW_OPEN,
            scale = 5f,
            offset = 0f,
            min = 0f,
            max = 1f,
        )
        val data = trackingData(mapOf(BlendShape.JAW_OPEN to 0.9f))
        val raw = data.blendShapes.valueOf(mapping.blendShape)
        val result = (raw * mapping.scale + mapping.offset).coerceIn(mapping.min, mapping.max)
        // 0.9 * 5 = 4.5, clamped to 1.0
        assertEquals(1f, result, TOLERANCE)
    }

    @Test
    fun customComputesFromFullData() {
        val mapping = ParameterMapping.Custom { data ->
            data.headTransform.yaw * 2f
        }
        val data = FaceTrackingData(
            blendShapes = emptyMap(),
            headTransform = HeadTransform(yaw = 10f),
            timestampMs = 0L,
            isTracking = true,
        )
        val result = mapping.compute(data)
        assertEquals(20f, result, TOLERANCE)
    }

    companion object {
        private const val TOLERANCE = 0.001f
    }
}
