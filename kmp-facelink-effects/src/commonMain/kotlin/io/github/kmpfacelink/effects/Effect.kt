package io.github.kmpfacelink.effects

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness

/**
 * A face effect definition. Each subtype describes how tracking data maps to output parameters.
 *
 * **Stability policy:**
 * - [AnchorEffect], [ExpressionEffect], [HandEffect], and [TransformEffect] are stable API.
 * - [AmbientEffect] is marked [io.github.kmpfacelink.ExperimentalFaceLinkApi] and may change
 *   in future releases. Opt in with `@OptIn(ExperimentalFaceLinkApi::class)`.
 *
 * @property id Unique identifier for this effect (used as key prefix in output)
 * @property enabled Whether this effect is active
 */
public sealed class Effect {
    public abstract val id: String
    public abstract val enabled: Boolean

    /**
     * Attaches to a face landmark and outputs an anchor position with optional head rotation.
     *
     * Output keys: `anchors["$id"]` → [AnchorData]
     *
     * @property anchor The face landmark to attach to
     * @property rotationSource How to compute the rotation component
     */
    public data class AnchorEffect(
        override val id: String,
        val anchor: AnchorPoint,
        val rotationSource: RotationSource = RotationSource.HEAD_TRANSFORM,
        override val enabled: Boolean = true,
    ) : Effect()

    /**
     * Driven by one or more blend shape values compared against a threshold.
     *
     * The average of the specified blend shapes is compared to [threshold].
     * If above, the intensity is computed via [mapping].
     *
     * Output keys: `parameters["$id.intensity"]`
     *
     * @property blendShapes Blend shapes to average
     * @property threshold Minimum average to activate (0.0–1.0)
     * @property mapping How to map the raw average to intensity
     */
    public data class ExpressionEffect(
        override val id: String,
        val blendShapes: List<BlendShape>,
        val threshold: Float = 0f,
        val mapping: IntensityMapping = IntensityMapping.Linear,
        override val enabled: Boolean = true,
    ) : Effect()

    /**
     * Driven by hand gesture detection.
     *
     * Output keys: `parameters["$id.intensity"]`
     *
     * @property gesture The gesture to detect
     * @property hand Required handedness, or null for either hand
     * @property minConfidence Minimum gesture confidence (0.0–1.0)
     */
    public data class HandEffect(
        override val id: String,
        val gesture: HandGesture,
        val hand: Handedness? = null,
        val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
        override val enabled: Boolean = true,
    ) : Effect()

    /**
     * Maps a blend shape value to a custom output parameter via a transform function.
     *
     * Output keys: `parameters["$id"]`
     *
     * @property blendShape The blend shape to read
     * @property transform Function that maps the blend shape value (0.0–1.0) to output
     */
    public data class TransformEffect(
        override val id: String,
        val blendShape: BlendShape,
        val transform: (Float) -> Float,
        override val enabled: Boolean = true,
    ) : Effect()

    /**
     * Custom ambient effect driven by any combination of face and hand data.
     *
     * Output keys: `parameters["$id.intensity"]`
     *
     * @property compute Function that receives optional face/hand data and returns intensity (0.0–1.0)
     */
    @ExperimentalFaceLinkApi
    public data class AmbientEffect(
        override val id: String,
        val compute: (face: FaceTrackingData?, hand: HandTrackingData?) -> Float,
        override val enabled: Boolean = true,
    ) : Effect()

    internal companion object {
        const val DEFAULT_MIN_CONFIDENCE = 0.5f
    }
}
