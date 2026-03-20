package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape

/**
 * Pre-allocated [HashMap] pool sized for the full [BlendShape] set (52 entries).
 *
 * Used by pipeline stages (enhance, calibrate, smooth) to avoid per-frame HashMap
 * allocations. Each stage holds its own instance and reuses it across frames.
 *
 * Not thread-safe — one instance per pipeline stage, used from a single thread.
 */
internal class BlendShapeMapPool {

    /** Number of entries in the [BlendShape] enum. */
    private val capacity: Int = BlendShape.entries.size

    /**
     * Create a new pre-sized [HashMap] ready to hold all blend shape values.
     * Clear before repopulating each frame.
     */
    fun create(): HashMap<BlendShape, Float> = HashMap(capacity)
}
