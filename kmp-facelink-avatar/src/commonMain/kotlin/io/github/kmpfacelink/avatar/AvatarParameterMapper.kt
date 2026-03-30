package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.FaceTrackingData

/**
 * Converts [FaceTrackingData] into avatar parameters.
 *
 * Each implementation targets a specific avatar format (e.g. Live2D Cubism, VRM).
 * The returned map uses format-specific parameter IDs as keys and normalized float values.
 *
 * Tracking data is produced by [io.github.kmpfacelink.api.FaceTracker] in the core module.
 *
 * @see io.github.kmpfacelink.avatar.Live2dParameterMapper
 * @see io.github.kmpfacelink.avatar.VrmParameterMapper
 */
public fun interface AvatarParameterMapper {

    /**
     * Maps face tracking data to avatar parameters.
     *
     * @param data the current frame's face tracking data
     * @return parameter ID to value map, or empty map if [data] is not tracking
     */
    public fun map(data: FaceTrackingData): Map<String, Float>
}
