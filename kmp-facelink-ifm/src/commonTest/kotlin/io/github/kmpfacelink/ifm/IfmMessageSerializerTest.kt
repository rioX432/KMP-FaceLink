package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.allShapesAtRest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IfmMessageSerializerTest {

    @Test
    fun serializeV1ContainsDashSeparator() {
        val message = IfmMessageSerializer.serialize(
            blendShapes = allShapesAtRest(),
            headTransform = HeadTransform(),
            gazeData = null,
            dataVersion = DataVersion.V1,
        )
        // V1 uses dash between name and value
        assertTrue(message.contains("eyeBlink_L-0|"), "V1 should use dash separator")
        assertFalse(message.contains("sendDataVersion=v2"), "V1 should not include version tag")
    }

    @Test
    fun serializeV2ContainsAmpersandSeparator() {
        val message = IfmMessageSerializer.serialize(
            blendShapes = allShapesAtRest(),
            headTransform = HeadTransform(),
            gazeData = null,
            dataVersion = DataVersion.V2,
        )
        // V2 uses ampersand between name and value
        assertTrue(message.contains("eyeBlink_L&0|"), "V2 should use ampersand separator")
        assertTrue(message.contains("sendDataVersion=v2|"), "V2 should include version tag")
    }

    @Test
    fun serializeBlendShapeValuesScaledTo100() {
        val blendShapes = mapOf(
            BlendShape.JAW_OPEN to 0.5f,
            BlendShape.EYE_BLINK_LEFT to 1.0f,
            BlendShape.MOUTH_SMILE_LEFT to 0.0f,
        )
        val message = IfmMessageSerializer.serialize(
            blendShapes = blendShapes,
            headTransform = HeadTransform(),
            gazeData = null,
            dataVersion = DataVersion.V1,
        )
        assertTrue(message.contains("jawOpen-50|"), "0.5 should scale to 50")
        assertTrue(message.contains("eyeBlink_L-100|"), "1.0 should scale to 100")
        assertTrue(message.contains("mouthSmile_L-0|"), "0.0 should scale to 0")
    }

    @Test
    fun serializeContainsHeadTransform() {
        val head = HeadTransform(
            pitch = 10.5f,
            yaw = -5.2f,
            roll = 3.0f,
            positionX = 0.1f,
            positionY = 0.2f,
            positionZ = 0.3f,
        )
        val message = IfmMessageSerializer.serialize(
            blendShapes = allShapesAtRest(),
            headTransform = head,
            gazeData = null,
            dataVersion = DataVersion.V1,
        )
        assertTrue(message.contains("=head#"), "Should contain head section")
        assertTrue(message.contains("10.500000"), "Should contain pitch")
        assertTrue(message.contains("-5."), "Should contain negative yaw")
    }

    @Test
    fun serializeContainsEyeRotations() {
        val gaze = GazeData(
            leftEyePitch = 5.0f,
            leftEyeYaw = -3.0f,
            rightEyePitch = 4.0f,
            rightEyeYaw = -2.0f,
        )
        val message = IfmMessageSerializer.serialize(
            blendShapes = allShapesAtRest(),
            headTransform = HeadTransform(),
            gazeData = gaze,
            dataVersion = DataVersion.V1,
        )
        assertTrue(message.contains("rightEye#"), "Should contain right eye section")
        assertTrue(message.contains("leftEye#"), "Should contain left eye section")
    }

    @Test
    fun serializeContainsAll52BlendShapes() {
        val message = IfmMessageSerializer.serialize(
            blendShapes = allShapesAtRest(),
            headTransform = HeadTransform(),
            gazeData = null,
            dataVersion = DataVersion.V1,
        )
        // Verify all 52 iFM blendshape names are present
        for ((_, ifmName) in IfmBlendShapeNames.entries) {
            assertTrue(message.contains("$ifmName-"), "Should contain $ifmName")
        }
        assertEquals(52, IfmBlendShapeNames.entries.size, "Should have 52 entries")
    }

    @Test
    fun serializeClampsBeyondRange() {
        val blendShapes = mapOf(BlendShape.JAW_OPEN to 1.5f)
        val message = IfmMessageSerializer.serialize(
            blendShapes = blendShapes,
            headTransform = HeadTransform(),
            gazeData = null,
            dataVersion = DataVersion.V1,
        )
        assertTrue(message.contains("jawOpen-100|"), "Values above 1.0 should clamp to 100")
    }
}
