package io.github.kmpfacelink.model

/**
 * Policy controlling how the tracker adapts to thermal pressure.
 */
public sealed class ThermalPolicy {
    /** No thermal management. Tracker runs at full speed regardless of temperature. */
    public data object Disabled : ThermalPolicy()

    /**
     * Adaptive thermal management that reduces processing load as temperature increases.
     *
     * @property fairTargetFps Target frame rate when thermal state is [ThermalState.FAIR].
     *           Default 25 fps.
     * @property seriousTargetFps Target frame rate when thermal state is [ThermalState.SERIOUS].
     *           Default 20 fps.
     * @property criticalTargetFps Target frame rate when thermal state is [ThermalState.CRITICAL].
     *           Default 15 fps.
     * @property disableEnhancerOnSerious Whether to disable BlendShapeEnhancer correlation
     *           when thermal state reaches [ThermalState.SERIOUS] or above. Android only.
     */
    public data class Adaptive(
        val fairTargetFps: Int = 25,
        val seriousTargetFps: Int = 20,
        val criticalTargetFps: Int = 15,
        val disableEnhancerOnSerious: Boolean = true,
    ) : ThermalPolicy() {
        init {
            require(fairTargetFps > 0) { "fairTargetFps must be positive, was $fairTargetFps" }
            require(seriousTargetFps > 0) { "seriousTargetFps must be positive, was $seriousTargetFps" }
            require(criticalTargetFps > 0) { "criticalTargetFps must be positive, was $criticalTargetFps" }
        }
    }
}
