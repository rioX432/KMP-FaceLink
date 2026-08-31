// sparse-contract-exempt: the Live Link Face wire array is 52 floats defined outside this SDK, so
// lowering into it has to fill every slot. What keeps that from being silent is `presentMask`
// below, which reports the same channels as a bit set. The flattening is unavoidable here; losing
// the record of it is not, and callers that skip the mask are choosing to.

package io.github.kmpfacelink.model

/**
 * Canonical ARKit **Live Link Face** 52-channel blendshape ordering and lowering.
 *
 * KMP-FaceLink's [BlendShape] enum is declared in *anatomical* groups (eyes, jaw,
 * mouth, brow, cheek, nose, tongue), which is **not** the order the Apple ARKit
 * Live Link Face protocol streams. Consumers that expect the Live Link wire order
 * (e.g. avatar-core, Unreal `ELiveLinkFaceBlendShape`, most VRM/ARKit pipelines)
 * must receive channels in the exact index order defined here.
 *
 * This object is the **single source of truth** for that ordering. It is shared by
 * every raw-tracking seam (the Android `FaceLinkBridgeFacade`, the iOS C bridge, and
 * any future consumer) so no platform hardcodes its own permutation.
 *
 * The order is the canonical ARKit **Live Link Face** wire order — identical to
 * Unreal Engine's `ELiveLinkFaceBlendShape` enum and the Live Link Face CSV/UDP
 * column order (channel 0 = `eyeBlinkLeft` … channel 51 = `tongueOut`).
 *
 * Mapping is always **by name** (via [BlendShape.arKitName] / [BlendShape.fromArKitName]),
 * never by [BlendShape.ordinal].
 *
 * @see BlendShape
 * @see <a href="https://developer.apple.com/documentation/arkit/arfaceanchor/blendshapelocation">ARKit BlendShapeLocation</a>
 */
public object LiveLinkBlendShapes {

    /** Number of blendshape channels in the Live Link protocol. */
    public const val COUNT: Int = 52

    /**
     * The 52 ARKit Live Link Face channel names (camelCase), in canonical wire order.
     *
     * Index `i` in a lowered [FloatArray] corresponds to `names[i]`.
     */
    public val names: List<String> = listOf(
        // Left eye (0-6)
        "eyeBlinkLeft",
        "eyeLookDownLeft",
        "eyeLookInLeft",
        "eyeLookOutLeft",
        "eyeLookUpLeft",
        "eyeSquintLeft",
        "eyeWideLeft",
        // Right eye (7-13)
        "eyeBlinkRight",
        "eyeLookDownRight",
        "eyeLookInRight",
        "eyeLookOutRight",
        "eyeLookUpRight",
        "eyeSquintRight",
        "eyeWideRight",
        // Jaw (14-17)
        "jawForward",
        "jawLeft",
        "jawRight",
        "jawOpen",
        // Mouth (18-40)
        "mouthClose",
        "mouthFunnel",
        "mouthPucker",
        "mouthLeft",
        "mouthRight",
        "mouthSmileLeft",
        "mouthSmileRight",
        "mouthFrownLeft",
        "mouthFrownRight",
        "mouthDimpleLeft",
        "mouthDimpleRight",
        "mouthStretchLeft",
        "mouthStretchRight",
        "mouthRollLower",
        "mouthRollUpper",
        "mouthShrugLower",
        "mouthShrugUpper",
        "mouthPressLeft",
        "mouthPressRight",
        "mouthLowerDownLeft",
        "mouthLowerDownRight",
        "mouthUpperUpLeft",
        "mouthUpperUpRight",
        // Brow (41-45)
        "browDownLeft",
        "browDownRight",
        "browInnerUp",
        "browOuterUpLeft",
        "browOuterUpRight",
        // Cheek (46-48)
        "cheekPuff",
        "cheekSquintLeft",
        "cheekSquintRight",
        // Nose (49-50)
        "noseSneerLeft",
        "noseSneerRight",
        // Tongue (51)
        "tongueOut",
    )

    /**
     * The 52 [BlendShape] enum values in Live Link wire order, resolved from [names]
     * **by name**. Index `i` corresponds to `names[i]`.
     */
    public val order: List<BlendShape> = names.map { name ->
        BlendShape.fromArKitName(name)
            ?: error("No BlendShape matches ARKit Live Link name '$name'")
    }

    // Internal permutation: [shape.ordinal] = Live Link channel index. Kept private so
    // the shared source-of-truth table cannot be mutated by callers.
    private val ordinalToLiveLinkIndexTable: IntArray = IntArray(COUNT).also { table ->
        for (liveLinkIndex in order.indices) {
            table[order[liveLinkIndex].ordinal] = liveLinkIndex
        }
    }

    /**
     * Returns a fresh permutation array mapping `BlendShape.ordinal` → Live Link channel
     * index. `result[shape.ordinal]` gives the position of `shape` in a Live Link array.
     *
     * Exposed so native bridges (iOS) can reorder an ordinal-indexed buffer into Live Link
     * order without re-implementing the mapping. A defensive copy is returned each call so
     * the shared table cannot be corrupted; bridges should cache the result once.
     */
    public fun ordinalToLiveLinkIndex(): IntArray = ordinalToLiveLinkIndexTable.copyOf()

    /**
     * Lowers a [BlendShapeData] map into a 52-element [FloatArray] in Live Link order.
     *
     * Channels absent from [blendShapes] (e.g. `tongueOut` on Android/MediaPipe, which does not
     * produce a tongue channel) are **0-filled**, and that zero is indistinguishable from a
     * value that was really there once it is in the array. Call [presentMask] alongside this to
     * keep the two apart.
     *
     * @param blendShapes tracker output keyed by [BlendShape]; missing keys become `0f`.
     * @return a fresh `FloatArray(52)` in canonical Live Link order.
     */
    public fun lower(blendShapes: BlendShapeData): FloatArray {
        val dense = blendShapes.densify()
        val out = FloatArray(COUNT)
        for (i in order.indices) {
            out[i] = dense.values[order[i].ordinal]
        }
        return out
    }

    /**
     * Bit set of the channels [lower] filled from a value rather than from the substitute.
     *
     * The wire array is fixed at [COUNT] floats, so a channel the estimator never produced still
     * occupies a slot and [lower] writes `0f` into it. That zero is indistinguishable from a
     * value that was really there, once it leaves this process. This mask is what a reader consults
     * to tell the two apart; bit `i` corresponds to channel `i` of [lower].
     *
     * The bit says the shape had a value in the map passed in. It does not say where that value
     * came from - the estimator, a landmark-derived solver, or an interpolated frame all set it.
     */
    public fun presentMask(blendShapes: BlendShapeData): Long {
        var mask = 0L
        for (i in order.indices) {
            if (order[i] in blendShapes) {
                mask = mask or (1L shl i)
            }
        }
        return mask
    }
}
