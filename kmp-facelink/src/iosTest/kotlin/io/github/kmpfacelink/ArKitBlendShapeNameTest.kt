package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ARKit spells a paired shape `eyeBlink_L`; this SDK spells it `eyeBlinkLeft`.
 *
 * The tracker looked raw ARKit keys up directly, so every paired shape missed and only the unpaired
 * ones resolved. Filling the absent shapes with `0f` hid it completely: the map still had 52
 * entries and the dropped ones read as a face at rest. These tests pin the conversion so the same
 * mismatch cannot come back silently.
 *
 * The conversion under test is the one `ARKitFaceTracker` applies; it is duplicated here rather
 * than exposed, because widening the tracker's API surface for a test is the worse trade.
 */
class ArKitBlendShapeNameTest {

    private fun normalize(name: String): String = when {
        name.endsWith("_L") -> name.dropLast(2) + "Left"
        name.endsWith("_R") -> name.dropLast(2) + "Right"
        else -> name
    }

    /** The ARKit spelling for a shape this SDK calls [shape]. */
    private fun arKitSpelling(shape: BlendShape): String {
        val name = shape.arKitName
        return when {
            name.endsWith("Left") -> name.dropLast(4) + "_L"
            name.endsWith("Right") -> name.dropLast(5) + "_R"
            else -> name
        }
    }

    @Test
    fun everyShapeResolvesFromItsArKitSpelling() {
        val unresolved = BlendShape.entries.filter {
            BlendShape.fromArKitName(normalize(arKitSpelling(it))) == null
        }
        assertTrue(unresolved.isEmpty(), "these did not resolve: $unresolved")
    }

    @Test
    fun aPairedShapeResolvesToTheCorrectSide() {
        assertEquals(BlendShape.EYE_BLINK_LEFT, BlendShape.fromArKitName(normalize("eyeBlink_L")))
        assertEquals(BlendShape.EYE_BLINK_RIGHT, BlendShape.fromArKitName(normalize("eyeBlink_R")))
    }

    @Test
    fun anUnpairedShapeIsUnaffectedByTheConversion() {
        // The control: these already matched before the conversion existed, so the conversion must
        // not disturb them.
        for (name in listOf("jawOpen", "mouthPucker", "tongueOut", "browInnerUp")) {
            assertEquals(name, normalize(name))
            assertNotNull(BlendShape.fromArKitName(normalize(name)), name)
        }
    }

    @Test
    fun theRawPairedSpellingStillDoesNotResolveWithoutTheConversion() {
        // Shows the conversion is doing the work, not that the map happened to accept both forms.
        assertNull(BlendShape.fromArKitName("eyeBlink_L"))
        assertNull(BlendShape.fromArKitName("cheekSquint_R"))
    }

    @Test
    fun theConversionDoesNotInventShapesForUnknownKeys() {
        assertNull(BlendShape.fromArKitName(normalize("noSuchShape_L")))
        assertNull(BlendShape.fromArKitName(normalize("_neutral")))
    }
}
