package io.github.kmpfacelink.model

/**
 * The blend shape vocabulary as it crosses a binary boundary.
 *
 * The C bridge, the Unity plugin and the React Native wrapper all lay blend shapes out as a fixed
 * array indexed by ordinal, and each of them keeps its own hand-written copy of the order. Nothing
 * in a compiler connects those copies to this enum: add a 53rd shape here and every ordinal past
 * the insertion point shifts on one side only, so each reader takes a believable number and files
 * it under the wrong expression. No crash, no type error, just wrong faces.
 *
 * [FINGERPRINT] is the value that turns that into a loud failure. Writing it into the wire header
 * lets a reader compare it against a fingerprint computed over its own table and refuse the frame
 * when the two vocabularies have drifted apart.
 */
public object BlendShapeWireSchema {

    /** Number of channels a wire buffer carries, one per [BlendShape]. */
    public val CHANNEL_COUNT: Int = BlendShape.entries.size

    /**
     * FNV-1a over every ARKit name in ordinal order, each followed by a comma.
     *
     * The trailing comma matters: without it "ab","c" and "a","bc" hash alike, which is exactly the
     * kind of rename this is meant to catch.
     */
    public val FINGERPRINT: Int by lazy {
        var hash = FNV_OFFSET_BASIS
        for (shape in BlendShape.entries) {
            for (char in shape.arKitName) {
                hash = (hash xor char.code) * FNV_PRIME
            }
            hash = (hash xor SEPARATOR.code) * FNV_PRIME
        }
        hash
    }

    /**
     * Bit set of the shapes present in [blendShapes], indexed by [BlendShape.ordinal].
     *
     * The companion to a fixed-size ordinal-ordered wire array, for the same reason
     * [LiveLinkBlendShapes.presentMask] exists: the array has a slot for every shape whether or
     * not one had a value, and only a mask distinguishes a written zero from an unwritten one.
     */
    public fun presentMaskByOrdinal(blendShapes: BlendShapeData): Long {
        var mask = 0L
        for (shape in blendShapes.keys) {
            mask = mask or (1L shl shape.ordinal)
        }
        return mask
    }

    private const val FNV_OFFSET_BASIS = -0x7EE3623B // 2166136261 as a signed Int
    private const val FNV_PRIME = 16777619
    private const val SEPARATOR = ','
}
