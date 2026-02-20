package io.github.kmpfacelink.model

/**
 * Configuration for [io.github.kmpfacelink.api.BodyTracker].
 *
 * @property smoothingConfig Smoothing filter configuration. Reuses [SmoothingConfig] from face tracking.
 * @property maxBodies Maximum number of bodies to detect (1-3)
 * @property cameraFacing Which camera to use
 */
public data class BodyTrackerConfig(
    val smoothingConfig: SmoothingConfig = SmoothingConfig.Ema(),
    val maxBodies: Int = 1,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
) {
    init {
        require(maxBodies in 1..MAX_BODIES) { "maxBodies must be 1-$MAX_BODIES, was $maxBodies" }
    }

    private companion object {
        private const val MAX_BODIES = 3
    }
}
