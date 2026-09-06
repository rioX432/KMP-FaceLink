package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.LiveLinkBlendShapes
import io.github.kmpfacelink.model.allShapesAtRest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveLinkBlendShapesTest {

    @Test
    fun countIs52() {
        assertEquals(52, LiveLinkBlendShapes.COUNT)
        assertEquals(52, LiveLinkBlendShapes.names.size)
        assertEquals(52, LiveLinkBlendShapes.order.size)
        assertEquals(52, LiveLinkBlendShapes.ordinalToLiveLinkIndex().size)
    }

    @Test
    fun namesMatchCanonicalLiveLinkWireOrder() {
        // Apple ARFaceAnchor.BlendShapeLocation declaration order == UE ELiveLinkFaceBlendShape.
        val expected = listOf(
            "eyeBlinkLeft", "eyeLookDownLeft", "eyeLookInLeft", "eyeLookOutLeft",
            "eyeLookUpLeft", "eyeSquintLeft", "eyeWideLeft",
            "eyeBlinkRight", "eyeLookDownRight", "eyeLookInRight", "eyeLookOutRight",
            "eyeLookUpRight", "eyeSquintRight", "eyeWideRight",
            "jawForward", "jawLeft", "jawRight", "jawOpen",
            "mouthClose", "mouthFunnel", "mouthPucker", "mouthLeft", "mouthRight",
            "mouthSmileLeft", "mouthSmileRight", "mouthFrownLeft", "mouthFrownRight",
            "mouthDimpleLeft", "mouthDimpleRight", "mouthStretchLeft", "mouthStretchRight",
            "mouthRollLower", "mouthRollUpper", "mouthShrugLower", "mouthShrugUpper",
            "mouthPressLeft", "mouthPressRight", "mouthLowerDownLeft", "mouthLowerDownRight",
            "mouthUpperUpLeft", "mouthUpperUpRight",
            "browDownLeft", "browDownRight", "browInnerUp", "browOuterUpLeft", "browOuterUpRight",
            "cheekPuff", "cheekSquintLeft", "cheekSquintRight",
            "noseSneerLeft", "noseSneerRight",
            "tongueOut",
        )
        assertEquals(expected, LiveLinkBlendShapes.names)
    }

    @Test
    fun orderResolvesEveryNameByName() {
        // order[i] must be the BlendShape whose arKitName == names[i] (never ordinal-derived).
        for (i in LiveLinkBlendShapes.names.indices) {
            assertEquals(LiveLinkBlendShapes.names[i], LiveLinkBlendShapes.order[i].arKitName)
        }
    }

    @Test
    fun orderContainsAll52DistinctBlendShapes() {
        assertEquals(BlendShape.entries.toSet(), LiveLinkBlendShapes.order.toSet())
        assertEquals(52, LiveLinkBlendShapes.order.toSet().size)
    }

    @Test
    fun tongueOutIsChannel51() {
        assertEquals(51, LiveLinkBlendShapes.names.lastIndex)
        assertEquals("tongueOut", LiveLinkBlendShapes.names[51])
        assertEquals(BlendShape.TONGUE_OUT, LiveLinkBlendShapes.order[51])
    }

    @Test
    fun permutationTableIsConsistentWithOrder() {
        val table = LiveLinkBlendShapes.ordinalToLiveLinkIndex()
        for (i in LiveLinkBlendShapes.order.indices) {
            val shape = LiveLinkBlendShapes.order[i]
            assertEquals(i, table[shape.ordinal])
        }
    }

    @Test
    fun ordinalToLiveLinkIndexReturnsDefensiveCopy() {
        val first = LiveLinkBlendShapes.ordinalToLiveLinkIndex()
        first[0] = 999 // mutating the returned copy must not affect the shared table
        assertEquals(0, LiveLinkBlendShapes.ordinalToLiveLinkIndex()[BlendShape.EYE_BLINK_LEFT.ordinal])
    }

    @Test
    fun lowerIdentityProducesAllZeros() {
        val out = LiveLinkBlendShapes.lower(allShapesAtRest())
        assertEquals(52, out.size)
        assertTrue(out.all { it == 0f }, "identity blendshapes must lower to all zeros")
    }

    @Test
    fun lowerPlacesValueAtCorrectLiveLinkIndex() {
        // jawOpen is Live Link channel 17.
        val out = LiveLinkBlendShapes.lower(mapOf(BlendShape.JAW_OPEN to 1.0f))
        assertEquals(1.0f, out[17])
        for (i in out.indices) {
            if (i != 17) assertEquals(0f, out[i], "only channel 17 should be set, got value at $i")
        }
    }

    @Test
    fun lowerMapsMultipleChannelsByName() {
        val input = mapOf(
            BlendShape.EYE_BLINK_LEFT to 0.5f, // channel 0
            BlendShape.EYE_BLINK_RIGHT to 0.6f, // channel 7
            BlendShape.MOUTH_SMILE_LEFT to 0.3f, // channel 23
        )
        val out = LiveLinkBlendShapes.lower(input)
        assertEquals(0.5f, out[0])
        assertEquals(0.6f, out[7])
        assertEquals(0.3f, out[23])
    }

    @Test
    fun lowerZeroFillsAbsentTongueChannel() {
        // MediaPipe/Android does not produce a tongue channel — key is absent from the map.
        val mediaPipeLike = BlendShape.entries
            .filter { it != BlendShape.TONGUE_OUT }
            .associateWith { 0.4f }
        val out = LiveLinkBlendShapes.lower(mediaPipeLike)
        assertEquals(0f, out[51], "absent tongueOut channel must be 0-filled")
        assertEquals(0.4f, out[17], "present channels must be preserved")
    }
}
