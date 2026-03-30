package io.github.kmpfacelink.model

/**
 * Configuration for 3D body pose lifting on platforms that only provide 2D landmarks.
 *
 * When enabled, a [LiftingStrategy] is applied to estimate z-depth from 2D joint positions.
 * This is primarily useful on Android where MediaPipe provides relative z values that can
 * be enhanced, or where only x/y data is available.
 *
 * @property enabled Whether 3D pose lifting is active. Defaults to false.
 * @property strategy The lifting algorithm to use. Defaults to [LiftingStrategy.STATISTICAL].
 * @property shoulderReferenceWidth Expected shoulder width as a fraction of image width,
 *           used by perspective-based strategies to estimate distance from camera.
 *           Defaults to 0.25 (typical for an adult at ~1.5m distance).
 */
public data class Body3DConfig(
    val enabled: Boolean = false,
    val strategy: LiftingStrategy = LiftingStrategy.STATISTICAL,
    val shoulderReferenceWidth: Float = DEFAULT_SHOULDER_REFERENCE_WIDTH,
) {
    init {
        require(shoulderReferenceWidth > 0f) {
            "shoulderReferenceWidth must be positive, was $shoulderReferenceWidth"
        }
    }

    public companion object {
        /** Default expected shoulder width as a fraction of image width. */
        public const val DEFAULT_SHOULDER_REFERENCE_WIDTH: Float = 0.25f
    }
}

/**
 * Available strategies for estimating depth from 2D body poses.
 */
public enum class LiftingStrategy {
    /**
     * Uses anthropometric body proportions to estimate relative joint depth.
     * Infers depth from limb foreshortening — when a projected limb appears shorter
     * than its expected length, it is estimated to be pointing toward/away from the camera.
     */
    STATISTICAL,

    /**
     * Estimates depth from perspective cues: shoulder width ratio indicates distance
     * from camera, and limb length ratios indicate relative depth of individual joints.
     * Combines global distance estimation with per-limb foreshortening analysis.
     */
    PERSPECTIVE,
}
