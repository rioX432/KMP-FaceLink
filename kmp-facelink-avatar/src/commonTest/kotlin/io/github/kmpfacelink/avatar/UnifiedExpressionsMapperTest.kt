package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedExpressionsMapperTest {

    private fun trackingData(
        blendShapes: Map<BlendShape, Float> = emptyMap(),
        headTransform: HeadTransform = HeadTransform(),
    ) = FaceTrackingData(
        blendShapes = blendShapes,
        headTransform = headTransform,
        timestampMs = 0L,
        isTracking = true,
    )

    // --- Not tracking ---

    @Test
    fun notTrackingReturnsEmptyMap() {
        val mapper = UnifiedExpressionsMapper()
        val data = FaceTrackingData.notTracking()
        assertTrue(mapper.map(data).isEmpty())
    }

    // --- Direct mappings ---

    @Test
    fun jawOpenMappedDirectly() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(blendShapes = mapOf(BlendShape.JAW_OPEN to 0.7f))
        val result = mapper.map(data)
        assertEquals(0.7f, result["v2/JawOpen"]!!, TOLERANCE)
    }

    @Test
    fun eyeClosedFromBlink() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.EYE_BLINK_LEFT to 0.9f,
                BlendShape.EYE_BLINK_RIGHT to 0.6f,
            ),
        )
        val result = mapper.map(data)
        assertEquals(0.9f, result["v2/EyeClosedLeft"]!!, TOLERANCE)
        assertEquals(0.6f, result["v2/EyeClosedRight"]!!, TOLERANCE)
    }

    @Test
    fun mouthSmileFromCornerPull() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to 0.8f,
                BlendShape.MOUTH_SMILE_RIGHT to 0.6f,
            ),
        )
        val result = mapper.map(data)
        assertEquals(0.8f, result["v2/MouthCornerPullLeft"]!!, TOLERANCE)
        assertEquals(0.6f, result["v2/MouthCornerPullRight"]!!, TOLERANCE)
    }

    @Test
    fun tongueOutMapped() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(blendShapes = mapOf(BlendShape.TONGUE_OUT to 0.5f))
        val result = mapper.map(data)
        assertEquals(0.5f, result["v2/TongueOut"]!!, TOLERANCE)
    }

    @Test
    fun lipSuckFromMouthRoll() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_ROLL_UPPER to 0.6f,
                BlendShape.MOUTH_ROLL_LOWER to 0.4f,
            ),
        )
        val result = mapper.map(data)
        assertEquals(0.6f, result["v2/LipSuckUpper"]!!, TOLERANCE)
        assertEquals(0.4f, result["v2/LipSuckLower"]!!, TOLERANCE)
    }

    // --- Blended shapes ---

    @Test
    fun eyeClosedBlendedAveragesBothEyes() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.EYE_BLINK_LEFT to 0.8f,
                BlendShape.EYE_BLINK_RIGHT to 0.4f,
            ),
        )
        val result = mapper.map(data)
        // (0.8 + 0.4) * 0.5 = 0.6
        assertEquals(0.6f, result["v2/EyeClosed"]!!, TOLERANCE)
    }

    @Test
    fun mouthSmileBlendedAverages() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to 0.6f,
                BlendShape.MOUTH_SMILE_RIGHT to 0.8f,
            ),
        )
        val result = mapper.map(data)
        // (0.6 + 0.8) * 0.5 = 0.7
        assertEquals(0.7f, result["v2/MouthSmile"]!!, TOLERANCE)
    }

    @Test
    fun mouthSadFromFrown() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_FROWN_LEFT to 0.5f,
                BlendShape.MOUTH_FROWN_RIGHT to 0.7f,
            ),
        )
        val result = mapper.map(data)
        assertEquals(0.5f, result["v2/MouthSadLeft"]!!, TOLERANCE)
        assertEquals(0.7f, result["v2/MouthSadRight"]!!, TOLERANCE)
        assertEquals(0.6f, result["v2/MouthSad"]!!, TOLERANCE)
    }

    @Test
    fun mouthOpenSubtractsMouthClose() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.JAW_OPEN to 0.8f,
                BlendShape.MOUTH_CLOSE to 0.3f,
            ),
        )
        val result = mapper.map(data)
        // 0.8 - 0.3 = 0.5
        assertEquals(0.5f, result["v2/MouthOpen"]!!, TOLERANCE)
    }

    @Test
    fun mouthOpenClampsToZero() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.JAW_OPEN to 0.2f,
                BlendShape.MOUTH_CLOSE to 0.8f,
            ),
        )
        val result = mapper.map(data)
        assertEquals(0.0f, result["v2/MouthOpen"]!!, TOLERANCE)
    }

    @Test
    fun lipSuckBlendedAverages() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_ROLL_UPPER to 0.8f,
                BlendShape.MOUTH_ROLL_LOWER to 0.4f,
            ),
        )
        val result = mapper.map(data)
        // (0.8 + 0.4) * 0.5 = 0.6
        assertEquals(0.6f, result["v2/LipSuck"]!!, TOLERANCE)
    }

    @Test
    fun cheekPuffSplitToBothSides() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(BlendShape.CHEEK_PUFF to 0.7f),
        )
        val result = mapper.map(data)
        assertEquals(0.7f, result["v2/CheekPuffLeft"]!!, TOLERANCE)
        assertEquals(0.7f, result["v2/CheekPuffRight"]!!, TOLERANCE)
        assertEquals(0.7f, result["v2/CheekPuff"]!!, TOLERANCE)
    }

    // --- Config: prefix ---

    @Test
    fun defaultPrefixIsV2() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(blendShapes = mapOf(BlendShape.JAW_OPEN to 0.5f))
        val result = mapper.map(data)
        assertTrue(result.keys.any { it.startsWith("v2/") })
    }

    @Test
    fun noPrefixConfig() {
        val mapper = UnifiedExpressionsMapper(
            UnifiedExpressionsConfig(parameterPrefix = UnifiedExpressionsConfig.NO_PREFIX),
        )
        val data = trackingData(blendShapes = mapOf(BlendShape.JAW_OPEN to 0.5f))
        val result = mapper.map(data)
        assertTrue("JawOpen" in result)
        assertFalse(result.keys.any { it.startsWith("v2/") })
    }

    @Test
    fun customPrefix() {
        val mapper = UnifiedExpressionsMapper(
            UnifiedExpressionsConfig(parameterPrefix = "FT/"),
        )
        val data = trackingData(blendShapes = mapOf(BlendShape.JAW_OPEN to 0.5f))
        val result = mapper.map(data)
        assertTrue("FT/JawOpen" in result)
    }

    // --- Config: blended shapes exclusion ---

    @Test
    fun excludeBlendedShapes() {
        val mapper = UnifiedExpressionsMapper(
            UnifiedExpressionsConfig(includeBlendedShapes = false),
        )
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.EYE_BLINK_LEFT to 0.5f,
                BlendShape.EYE_BLINK_RIGHT to 0.5f,
            ),
        )
        val result = mapper.map(data)
        // Base shapes should be present
        assertTrue("v2/EyeClosedLeft" in result)
        assertTrue("v2/EyeClosedRight" in result)
        // Blended shapes should be absent
        assertFalse("v2/EyeClosed" in result)
        assertFalse("v2/MouthSmile" in result)
    }

    // --- Config: weight overrides ---

    @Test
    fun weightOverrideApplied() {
        val mapper = UnifiedExpressionsMapper(
            UnifiedExpressionsConfig(
                weightOverrides = mapOf("JawOpen" to 1.5f),
            ),
        )
        val data = trackingData(blendShapes = mapOf(BlendShape.JAW_OPEN to 0.6f))
        val result = mapper.map(data)
        // 0.6 * 1.5 = 0.9
        assertEquals(0.9f, result["v2/JawOpen"]!!, TOLERANCE)
    }

    @Test
    fun weightOverrideClampedToOne() {
        val mapper = UnifiedExpressionsMapper(
            UnifiedExpressionsConfig(
                weightOverrides = mapOf("JawOpen" to 5.0f),
            ),
        )
        val data = trackingData(blendShapes = mapOf(BlendShape.JAW_OPEN to 0.8f))
        val result = mapper.map(data)
        // 0.8 * 5.0 = 4.0, clamped to 1.0
        assertEquals(1.0f, result["v2/JawOpen"]!!, TOLERANCE)
    }

    // --- Config: custom mappings ---

    @Test
    fun customMappingAdded() {
        val mapper = UnifiedExpressionsMapper(
            UnifiedExpressionsConfig(
                customMappings = mapOf(
                    "CustomParam" to ParameterMapping.Direct(BlendShape.CHEEK_PUFF),
                ),
            ),
        )
        val data = trackingData(blendShapes = mapOf(BlendShape.CHEEK_PUFF to 0.4f))
        val result = mapper.map(data)
        assertEquals(0.4f, result["v2/CustomParam"]!!, TOLERANCE)
    }

    // --- Nose / Brow ---

    @Test
    fun noseSneerMapped() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.NOSE_SNEER_LEFT to 0.3f,
                BlendShape.NOSE_SNEER_RIGHT to 0.5f,
            ),
        )
        val result = mapper.map(data)
        assertEquals(0.3f, result["v2/NoseSneerLeft"]!!, TOLERANCE)
        assertEquals(0.5f, result["v2/NoseSneerRight"]!!, TOLERANCE)
        assertEquals(0.4f, result["v2/NoseSneer"]!!, TOLERANCE)
    }

    @Test
    fun browDownMapped() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.BROW_DOWN_LEFT to 0.6f,
                BlendShape.BROW_DOWN_RIGHT to 0.4f,
            ),
        )
        val result = mapper.map(data)
        assertEquals(0.6f, result["v2/BrowDownLeft"]!!, TOLERANCE)
        assertEquals(0.4f, result["v2/BrowDownRight"]!!, TOLERANCE)
        assertEquals(0.5f, result["v2/BrowDown"]!!, TOLERANCE)
    }

    @Test
    fun browInnerUpMapped() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(
            blendShapes = mapOf(BlendShape.BROW_INNER_UP to 0.7f),
        )
        val result = mapper.map(data)
        assertEquals(0.7f, result["v2/BrowInnerUp"]!!, TOLERANCE)
    }

    // --- Edge cases ---

    @Test
    fun zeroBlendShapesProduceZeroOutput() {
        val mapper = UnifiedExpressionsMapper()
        val data = trackingData(blendShapes = emptyMap())
        val result = mapper.map(data)
        assertTrue(result.isNotEmpty())
        result.values.forEach { value ->
            assertEquals(0.0f, value, TOLERANCE)
        }
    }

    @Test
    fun allValuesClampedToZeroOne() {
        val mapper = UnifiedExpressionsMapper(
            UnifiedExpressionsConfig(
                weightOverrides = mapOf("EyeClosedLeft" to 10f),
            ),
        )
        val data = trackingData(
            blendShapes = mapOf(BlendShape.EYE_BLINK_LEFT to 1.0f),
        )
        val result = mapper.map(data)
        result.values.forEach { value ->
            assertTrue(value in 0f..1f, "Value $value out of range")
        }
    }

    companion object {
        private const val TOLERANCE = 0.01f
    }
}
