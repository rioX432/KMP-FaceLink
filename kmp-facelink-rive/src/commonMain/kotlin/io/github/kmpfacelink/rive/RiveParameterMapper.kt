package io.github.kmpfacelink.rive

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.FaceTrackingData

/**
 * Maps [FaceTrackingData] to Rive State Machine inputs.
 *
 * Each implementation defines how face tracking parameters are converted to
 * Rive input names and values. Use [RiveDefaultMappings] for ARKit blend shape
 * pass-through, or implement custom mapping logic.
 */
@ExperimentalFaceLinkApi
public fun interface RiveParameterMapper {

    /**
     * Maps face tracking data to Rive State Machine inputs.
     *
     * @param data the current frame's face tracking data
     * @return input name to [RiveInput] map, or empty map if [data] is not tracking
     */
    public fun map(data: FaceTrackingData): Map<String, RiveInput>
}
