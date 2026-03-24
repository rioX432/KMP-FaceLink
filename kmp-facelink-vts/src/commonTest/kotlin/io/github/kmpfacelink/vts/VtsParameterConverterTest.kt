package io.github.kmpfacelink.vts

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VtsParameterConverterTest {

    @Test
    fun eyeBlinkInvertedToEyeOpen() {
        val blendShapes = mapOf(
            BlendShape.EYE_BLINK_LEFT to 1.0f,
            BlendShape.EYE_BLINK_RIGHT to 0.0f,
        )
        val params = VtsParameterConverter.convertBlendShapes(blendShapes)
        val eyeLeft = params.find { it.id == "EyeOpenLeft" }
        val eyeRight = params.find { it.id == "EyeOpenRight" }
        assertNotNull(eyeLeft, "EyeOpenLeft should be present")
        assertNotNull(eyeRight, "EyeOpenRight should be present")
        // Blink 1.0 (fully closed) -> EyeOpen 0.0 (fully closed)
        assertEquals(0.0f, eyeLeft.value, "Fully blinked should map to 0")
        // Blink 0.0 (fully open) -> EyeOpen 1.0 (fully open)
        assertEquals(1.0f, eyeRight.value, "Not blinked should map to 1")
    }

    @Test
    fun mouthSmileConvertedCorrectly() {
        val blendShapes = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 0.7f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.5f,
        )
        val params = VtsParameterConverter.convertBlendShapes(blendShapes)
        val smile = params.find { it.id == "MouthSmile" }
        assertNotNull(smile, "MouthSmile should be present")
        // Should use the larger absolute value
        assertEquals(0.7f, smile.value)
    }

    @Test
    fun jawOpenConvertedToMouthOpen() {
        val blendShapes = mapOf(BlendShape.JAW_OPEN to 0.6f)
        val params = VtsParameterConverter.convertBlendShapes(blendShapes)
        val mouthOpen = params.find { it.id == "MouthOpen" }
        assertNotNull(mouthOpen, "MouthOpen should be present")
        assertEquals(0.6f, mouthOpen.value)
    }

    @Test
    fun headTransformConverted() {
        val head = HeadTransform(
            pitch = 10f,
            yaw = -5f,
            roll = 3f,
            positionX = 0.1f,
            positionY = 0.2f,
            positionZ = 0.3f,
        )
        val params = VtsParameterConverter.convertHeadTransform(head)
        assertEquals(6, params.size)
        val angleX = params.find { it.id == "FaceAngleX" }
        val angleY = params.find { it.id == "FaceAngleY" }
        val angleZ = params.find { it.id == "FaceAngleZ" }
        assertNotNull(angleX)
        assertNotNull(angleY)
        assertNotNull(angleZ)
        assertEquals(-5f, angleX.value, "FaceAngleX should be yaw")
        assertEquals(10f, angleY.value, "FaceAngleY should be pitch")
        assertEquals(3f, angleZ.value, "FaceAngleZ should be roll")
    }

    @Test
    fun emptyBlendShapesProduceParameters() {
        val blendShapes = emptyBlendShapeData()
        val params = VtsParameterConverter.convertBlendShapes(blendShapes)
        // All zero blendshapes should still produce VTS parameters
        assertTrue(params.isNotEmpty(), "Should produce parameters even with zero values")
    }

    @Test
    fun valuesClampedToRange() {
        val blendShapes = mapOf(
            BlendShape.JAW_OPEN to 1.5f,
            BlendShape.MOUTH_SMILE_LEFT to -0.5f,
        )
        val params = VtsParameterConverter.convertBlendShapes(blendShapes)
        val mouthOpen = params.find { it.id == "MouthOpen" }
        val smile = params.find { it.id == "MouthSmile" }
        assertNotNull(mouthOpen)
        assertNotNull(smile)
        assertEquals(1.0f, mouthOpen.value, "Should clamp to 1.0")
        assertEquals(0.0f, smile.value, "Should clamp negative to 0.0")
    }

    @Test
    fun unmappedBlendShapesIgnored() {
        // EYE_SQUINT_LEFT doesn't have a direct VTS mapping in the current map
        val blendShapes = mapOf(BlendShape.EYE_SQUINT_LEFT to 0.5f)
        val params = VtsParameterConverter.convertBlendShapes(blendShapes)
        // Squint isn't in the mapping, so no parameters should be produced
        assertTrue(params.isEmpty() || params.none { it.id.contains("Squint") })
    }
}
