package io.github.kmpfacelink.model

/**
 * Device-adaptive tracking quality preset bundling camera analysis resolution
 * and [PowerMode] into a single named tier.
 *
 * Preset expansion:
 *
 * | Preset   | [PowerMode] (⇒ baseFps) | Analysis resolution |
 * | -------- | ----------------------- | ------------------- |
 * | [Low]    | `LOW_POWER` (15 FPS)    | 320×240             |
 * | [Medium] | `BALANCED` (30 FPS)     | 480×360             |
 * | [High]   | `HIGH` (60 FPS)         | 640×480             |
 *
 * Frame rate is expressed indirectly through the preset's [powerMode]; read
 * `QualityPreset.X.powerMode.baseFps` if you need the nominal FPS. Using
 * [PowerMode] as the source of truth avoids a second FPS field on the config
 * that could drift from [PowerMode.baseFps].
 *
 * **Platform effect**: [analysisWidth] and [analysisHeight] only affect the
 * camera pipeline on Android (see [FaceTrackerConfig.analysisWidth] /
 * [FaceTrackerConfig.analysisHeight] — both are marked "Android only"). On
 * iOS, JVM, and Wasm the preset still produces a config but only
 * [powerMode] has an observable effect; camera resolution on those platforms
 * is handled by ARKit / JavaCV / browser APIs respectively. That is fine in
 * practice because the platforms with CPU/thermal constraints that motivated
 * this feature are exactly the Android devices where the analysis stream is
 * the lever we need.
 *
 * Auto-selection is driven by a [DeviceCapabilityProfile]; see
 * [autoSelect]. Callers that want fine-grained control can still set
 * [FaceTrackerConfig.powerMode], [FaceTrackerConfig.analysisWidth], and
 * [FaceTrackerConfig.analysisHeight] directly — the preset is a convenience
 * entry point that produces a pre-configured tracker config without forcing
 * use of the finer-grained fields.
 *
 * Presets are deliberately separate from [PowerMode] so that adding or
 * retuning a quality tier does not break the public [PowerMode] API, and so
 * that resolution changes (which [PowerMode] does not cover) can be bundled
 * with a single user-facing choice.
 */
public sealed class QualityPreset {

    /** Camera analysis stream width in pixels. Android only. */
    public abstract val analysisWidth: Int

    /** Camera analysis stream height in pixels. Android only. */
    public abstract val analysisHeight: Int

    /**
     * Power mode the tracker should run under when this preset is applied.
     * Its [PowerMode.baseFps] is the nominal frame rate for the preset:
     * Low → 15, Medium → 30, High → 60. Actual FPS at runtime is subject to
     * thermal, battery, and idle adjustments in `PowerModeManager`.
     */
    public abstract val powerMode: PowerMode

    /**
     * Returns a copy of [base] with this preset's `powerMode`,
     * `analysisWidth`, and `analysisHeight` applied. All other fields (model
     * asset path, smoothing config, blend-shape filter, etc.) are preserved
     * so callers can combine a quality preset with any custom configuration.
     */
    public fun applyTo(base: FaceTrackerConfig = FaceTrackerConfig()): FaceTrackerConfig =
        base.copy(
            powerConfig = base.powerConfig.copy(powerMode = powerMode),
            performanceConfig = base.performanceConfig.copy(
                analysisWidth = analysisWidth,
                analysisHeight = analysisHeight,
            ),
        )

    /**
     * Low-quality preset — [PowerMode.LOW_POWER] (15 FPS), 320×240.
     *
     * Targets budget devices and thermal-constrained sessions. The low
     * resolution reduces MediaPipe preprocess cost by roughly 4× versus the
     * default 640×480, freeing CPU/GPU budget for the rest of the app.
     */
    public data object Low : QualityPreset() {
        override val analysisWidth: Int = LOW_ANALYSIS_WIDTH
        override val analysisHeight: Int = LOW_ANALYSIS_HEIGHT
        override val powerMode: PowerMode = PowerMode.LOW_POWER
    }

    /**
     * Balanced preset — [PowerMode.BALANCED] (30 FPS), 480×360.
     *
     * The default target for most mid-range 2021+ mobile chipsets and for
     * browsers that do not expose WebGPU. Matches the `PowerModeManager`
     * balanced-mode thermal curve that already ships in the library.
     */
    public data object Medium : QualityPreset() {
        override val analysisWidth: Int = MEDIUM_ANALYSIS_WIDTH
        override val analysisHeight: Int = MEDIUM_ANALYSIS_HEIGHT
        override val powerMode: PowerMode = PowerMode.BALANCED
    }

    /**
     * High preset — [PowerMode.HIGH] (60 FPS), 640×480.
     *
     * Targets NPU-equipped flagship chipsets, Apple Silicon devices, and
     * modern desktops. Uses the existing
     * [FaceTrackerConfig.DEFAULT_ANALYSIS_WIDTH] /
     * [FaceTrackerConfig.DEFAULT_ANALYSIS_HEIGHT] dimensions so applying
     * this preset produces the analysis buffer the library has always used
     * — only the `powerMode` becomes authoritative.
     */
    public data object High : QualityPreset() {
        override val analysisWidth: Int = FaceTrackerConfig.DEFAULT_ANALYSIS_WIDTH
        override val analysisHeight: Int = FaceTrackerConfig.DEFAULT_ANALYSIS_HEIGHT
        override val powerMode: PowerMode = PowerMode.HIGH
    }

    public companion object {
        private const val LOW_ANALYSIS_WIDTH = 320
        private const val LOW_ANALYSIS_HEIGHT = 240

        private const val MEDIUM_ANALYSIS_WIDTH = 480
        private const val MEDIUM_ANALYSIS_HEIGHT = 360

        /**
         * Picks a [QualityPreset] that the device described by [profile] can
         * realistically sustain.
         *
         * Resolution rules (first match wins):
         * 1. [High] if the profile reports a vendor NPU **or** an explicit
         *    [DeviceTier.HIGH]. NPU devices dominate the "can handle 60 FPS"
         *    population, and HIGH-tier devices without an NPU are still fast
         *    enough to run 60 FPS MediaPipe tracking.
         * 2. [Medium] if the profile reports GPU acceleration **or** an
         *    explicit [DeviceTier.MID]. Devices with a GPU compute path can
         *    sustain 30 FPS comfortably.
         * 3. [Low] otherwise — CPU-only, low-tier, or unknown devices.
         *
         * The three rules are intentionally conservative: pushing a device
         * beyond its real capability causes thermal throttling, whereas
         * picking a lower tier only costs visual quality, not session
         * stability.
         */
        public fun autoSelect(profile: DeviceCapabilityProfile): QualityPreset {
            // Only a path known to be there earns an upgrade. An unknown path is treated the same
            // as an absent one, so a target that never probed does not get promoted on the strength
            // of a check it did not run.
            val npuAvailable = profile.npuInference.state == CapabilityState.AVAILABLE
            val gpuAvailable = profile.gpuInference.state == CapabilityState.AVAILABLE
            return when {
                npuAvailable -> High
                profile.qualityRecommendation.tier == DeviceTier.HIGH -> High
                gpuAvailable -> Medium
                profile.qualityRecommendation.tier == DeviceTier.MID -> Medium
                else -> Low
            }
        }
    }
}
