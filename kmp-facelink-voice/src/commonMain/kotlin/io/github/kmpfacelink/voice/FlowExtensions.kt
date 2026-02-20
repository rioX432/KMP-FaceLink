package io.github.kmpfacelink.voice

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.voice.lipsync.LipSyncFrame
import io.github.kmpfacelink.voice.lipsync.VisemeBlendShapes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Merges lip sync mouth shapes into face tracking data.
 *
 * Mouth-related blend shapes (JAW_*, MOUTH_*, TONGUE_OUT) from [lipSync]
 * **override** face tracking values. Eyes, brows, cheeks, and nose pass through unchanged.
 *
 * @param lipSync Flow of lip sync frames from [VoicePipeline] or [io.github.kmpfacelink.voice.lipsync.LipSyncEngine]
 * @return Flow of [FaceTrackingData] with lip sync overrides applied
 */
public fun Flow<FaceTrackingData>.withLipSync(
    lipSync: Flow<LipSyncFrame>,
): Flow<FaceTrackingData> = combine(lipSync) { tracking, frame ->
    if (frame.blendShapes.isEmpty()) return@combine tracking

    val mouthShapes = VisemeBlendShapes.mouthBlendShapes
    val merged = tracking.blendShapes.toMutableMap()

    // Override mouth-related shapes from lip sync
    for ((shape, value) in frame.blendShapes) {
        if (shape in mouthShapes) {
            merged[shape] = value
        }
    }

    tracking.copy(blendShapes = merged)
}

/**
 * Merges lip sync blend shape maps into face tracking data.
 *
 * Convenience overload that accepts raw `Map<BlendShape, Float>` from [VoicePipeline.lipSyncOutput].
 *
 * @param lipSyncShapes Flow of blend shape maps
 * @return Flow of [FaceTrackingData] with lip sync overrides applied
 */
public fun Flow<FaceTrackingData>.withLipSyncShapes(
    lipSyncShapes: Flow<Map<BlendShape, Float>>,
): Flow<FaceTrackingData> = combine(lipSyncShapes) { tracking, shapes ->
    if (shapes.isEmpty()) return@combine tracking

    val mouthShapes = VisemeBlendShapes.mouthBlendShapes
    val merged = tracking.blendShapes.toMutableMap()

    for ((shape, value) in shapes) {
        if (shape in mouthShapes) {
            merged[shape] = value
        }
    }

    tracking.copy(blendShapes = merged)
}
