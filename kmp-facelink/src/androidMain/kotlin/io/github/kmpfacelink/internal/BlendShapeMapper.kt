package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.util.BlendShapeMapPool

/**
 * Maps MediaPipe FaceLandmarker blend shape output to our [BlendShape] enum.
 *
 * MediaPipe outputs 53 categories (index 0 = "_neutral", which is skipped).
 * The remaining 52 use the same naming as ARKit.
 */
internal object BlendShapeMapper {

    /**
     * MediaPipe blend shape category name → [BlendShape] enum.
     * MediaPipe uses the exact same camelCase naming as ARKit.
     */
    private val nameToBlendShape: Map<String, BlendShape> by lazy {
        BlendShape.entries.associateBy { it.arKitName }
    }

    // Pre-allocated scratch map reused across calls on the analysis thread.
    // mapFromMediaPipe is always called from a single executor thread (analysisExecutor),
    // so no synchronization is needed. Callers that store the result must copy via HashMap(result).
    private val scratch: HashMap<BlendShape, Float> = BlendShapeMapPool().create()

    /**
     * Convert MediaPipe blend shape categories to [BlendShapeData].
     *
     * The returned map is the internal scratch buffer — copy it if you need to hold a reference
     * across the next [mapFromMediaPipe] call.
     *
     * @param categories List of (name, score) pairs from MediaPipe FaceLandmarkerResult
     */
    fun mapFromMediaPipe(categories: List<Pair<String, Float>>): BlendShapeData {
        scratch.clear()
        for ((name, score) in categories) {
            if (name == "_neutral") continue
            val shape = nameToBlendShape[name] ?: continue
            scratch[shape] = score.coerceIn(0f, 1f)
        }

        // Shapes MediaPipe does not carry stay absent. `tongueOut` has no MediaPipe counterpart,
        // and writing 0f for it would be indistinguishable from a measured resting tongue.
        return scratch
    }
}
