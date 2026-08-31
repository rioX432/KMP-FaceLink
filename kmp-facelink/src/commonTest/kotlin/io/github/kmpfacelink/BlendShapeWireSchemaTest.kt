package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeWireSchema
import io.github.kmpfacelink.model.LiveLinkBlendShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The fingerprint only earns its place if it changes when the vocabulary changes.
 *
 * A hash that stayed the same under a rename or an insertion would pass every check while the
 * drift it exists to catch went through, so each case below alters the vocabulary in one of the
 * ways a reader cannot otherwise detect and requires the fingerprint to move.
 */
class BlendShapeWireSchemaTest {

    /** The same computation the fingerprint uses, over an arbitrary list of names. */
    private fun fingerprintOf(names: List<String>): Int {
        var hash = -0x7EE3623B
        for (name in names) {
            for (char in name) {
                hash = (hash xor char.code) * 16777619
            }
            hash = (hash xor ','.code) * 16777619
        }
        return hash
    }

    private val currentNames: List<String> = BlendShape.entries.map { it.arKitName }

    @Test
    fun channelCountMatchesTheVocabulary() {
        assertEquals(BlendShape.entries.size, BlendShapeWireSchema.CHANNEL_COUNT)
    }

    @Test
    fun fingerprintIsComputedOverTheOrderedNames() {
        assertEquals(fingerprintOf(currentNames), BlendShapeWireSchema.FINGERPRINT)
    }

    @Test
    fun fingerprintIsStableAcrossReads() {
        assertEquals(BlendShapeWireSchema.FINGERPRINT, BlendShapeWireSchema.FINGERPRINT)
    }

    @Test
    fun addingAShapeChangesTheFingerprint() {
        assertNotEquals(
            BlendShapeWireSchema.FINGERPRINT,
            fingerprintOf(currentNames + "browWiggle"),
            "a 53rd shape shifts every ordinal past it and must not hash the same",
        )
    }

    @Test
    fun reorderingTwoShapesChangesTheFingerprint() {
        val swapped = currentNames.toMutableList()
        swapped[0] = currentNames[1]
        swapped[1] = currentNames[0]
        assertNotEquals(BlendShapeWireSchema.FINGERPRINT, fingerprintOf(swapped))
    }

    @Test
    fun renamingAShapeChangesTheFingerprint() {
        val renamed = currentNames.toMutableList()
        renamed[renamed.lastIndex] = renamed.last() + "Renamed"
        assertNotEquals(BlendShapeWireSchema.FINGERPRINT, fingerprintOf(renamed))
    }

    // region observed mask

    @Test
    fun theMaskMarksOnlyTheShapesTheEstimatorProduced() {
        val measured = mapOf(BlendShape.JAW_OPEN to 0.4f, BlendShape.CHEEK_PUFF to 0f)
        val mask = BlendShapeWireSchema.presentMaskByOrdinal(measured)
        for (shape in BlendShape.entries) {
            val bitSet = (mask and (1L shl shape.ordinal)) != 0L
            assertEquals(shape in measured, bitSet, "$shape")
        }
    }

    @Test
    fun aShapeMeasuredAtZeroIsStillMarkedObserved() {
        // This is the case the mask exists for: the wire slot holds 0f either way, and only the
        // mask says whether an estimator put it there.
        val mask = BlendShapeWireSchema.presentMaskByOrdinal(mapOf(BlendShape.CHEEK_PUFF to 0f))
        assertNotEquals(0L, mask and (1L shl BlendShape.CHEEK_PUFF.ordinal))
    }

    @Test
    fun anEmptyFrameMarksNothingObserved() {
        assertEquals(0L, BlendShapeWireSchema.presentMaskByOrdinal(emptyMap()))
    }

    @Test
    fun liveLinkMaskFollowsLiveLinkChannelOrderNotOrdinalOrder() {
        // The two orderings disagree, so a consumer that used one mask with the other array would
        // mark the wrong channels. Pin that they are genuinely different.
        val measured = mapOf(BlendShape.JAW_OPEN to 0.4f)
        assertNotEquals(
            BlendShapeWireSchema.presentMaskByOrdinal(measured),
            LiveLinkBlendShapes.presentMask(measured),
        )
    }

    @Test
    fun everyChannelLoweredFromAMeasurementIsMarkedInTheLiveLinkMask() {
        val measured = BlendShape.entries.take(10).associateWith { 0.5f }
        val mask = LiveLinkBlendShapes.presentMask(measured)
        val lowered = LiveLinkBlendShapes.lower(measured)
        var marked = 0
        for (i in lowered.indices) {
            if ((mask and (1L shl i)) != 0L) marked += 1
        }
        assertEquals(measured.size, marked)
    }

    // endregion

    @Test
    fun theSeparatorKeepsAdjacentNamesFromBlurringTogether() {
        // Without a separator "ab","c" and "a","bc" would hash alike, so a rename that moved a
        // character between neighbours would slip through.
        assertNotEquals(fingerprintOf(listOf("ab", "c")), fingerprintOf(listOf("a", "bc")))
    }
}
