package io.github.kmpfacelink.model

import io.github.kmpfacelink.util.FaceLinkLogger

/**
 * Configuration for [io.github.kmpfacelink.api.HolisticTracker].
 *
 * Enables simultaneous face, hand, and body tracking through a single
 * unified pipeline sharing one camera session.
 *
 * ## Property priority rules
 *
 * Top-level properties on this class **always override** the corresponding values
 * in [faceConfig], [handConfig], and [bodyConfig]. When a holistic tracker is
 * created, the platform implementation copies each sub-tracker config with the
 * top-level values applied, so any value set directly on a sub-config for an
 * overlapping property is silently replaced.
 *
 * The overlapping properties subject to this rule are:
 * - [cameraFacing] overrides `faceConfig.cameraFacing`, `handConfig.cameraFacing`,
 *   `bodyConfig.cameraFacing`
 * - [thermalPolicy] overrides `faceConfig.powerConfig.thermalPolicy`,
 *   `handConfig.thermalPolicy`, `bodyConfig.thermalPolicy`
 * - [inferenceDelegate] overrides `faceConfig.performanceConfig.inferenceDelegate`,
 *   `handConfig.inferenceDelegate`, `bodyConfig.inferenceDelegate`
 * - [analysisWidth] / [analysisHeight] override per-tracker analysis dimensions
 *   (Android only; the shared camera session uses these values)
 *
 * If you need different values per modality, use individual
 * [io.github.kmpfacelink.api.FaceTracker], [io.github.kmpfacelink.api.HandTracker],
 * and [io.github.kmpfacelink.api.BodyTracker] instead of the holistic tracker.
 *
 * A warning is logged at construction time when conflicting values are detected
 * between top-level properties and sub-config properties.
 *
 * @property enableFace Whether to enable face tracking
 * @property enableHand Whether to enable hand tracking
 * @property enableBody Whether to enable body tracking
 * @property faceConfig Configuration for the face tracker (used when [enableFace] is true).
 *           **Note:** [cameraFacing], [thermalPolicy], and [inferenceDelegate] set on this
 *           config are ignored — the top-level values on [HolisticTrackerConfig] take precedence.
 * @property handConfig Configuration for the hand tracker (used when [enableHand] is true).
 *           **Note:** [cameraFacing], [thermalPolicy], and [inferenceDelegate] set on this
 *           config are ignored — the top-level values on [HolisticTrackerConfig] take precedence.
 * @property bodyConfig Configuration for the body tracker (used when [enableBody] is true).
 *           **Note:** [cameraFacing], [thermalPolicy], and [inferenceDelegate] set on this
 *           config are ignored — the top-level values on [HolisticTrackerConfig] take precedence.
 * @property cameraFacing Which camera to use. **Overrides** per-tracker camera facing settings.
 * @property thermalPolicy Policy controlling how all sub-trackers adapt to thermal pressure.
 *           **Overrides** per-tracker thermal policy settings. Defaults to [ThermalPolicy.Disabled].
 *           **Ignored when [powerMode] is set.**
 * @property powerMode Power management profile for all sub-trackers. When non-null,
 *           overrides both [thermalPolicy] and per-tracker power mode settings.
 * @property inferenceDelegate Preferred hardware accelerator for ML model inference.
 *           **Overrides** per-tracker inference delegate settings.
 *           Android (MediaPipe) only. Defaults to [InferenceDelegate.AUTO].
 * @property analysisWidth Width of the shared camera analysis stream in pixels. Android only.
 *           **Overrides** per-tracker analysis width settings for the shared session.
 *           Lower values improve performance at the cost of accuracy. Defaults to 640.
 * @property analysisHeight Height of the shared camera analysis stream in pixels. Android only.
 *           **Overrides** per-tracker analysis height settings for the shared session.
 *           Lower values improve performance at the cost of accuracy. Defaults to 480.
 * @property fuseBodyAndHandArm When true, fuses body pose arm joints (shoulder, elbow, wrist)
 *           with hand tracker wrist landmarks into unified [ArmTrackingData] exposed in
 *           [HolisticTrackingData.arm]. Requires both [enableHand] and [enableBody] to be true.
 *           Defaults to false.
 */
public data class HolisticTrackerConfig(
    val enableFace: Boolean = true,
    val enableHand: Boolean = true,
    val enableBody: Boolean = true,
    val faceConfig: FaceTrackerConfig = FaceTrackerConfig(),
    val handConfig: HandTrackerConfig = HandTrackerConfig(),
    val bodyConfig: BodyTrackerConfig = BodyTrackerConfig(),
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val thermalPolicy: ThermalPolicy = ThermalPolicy.Disabled,
    val powerMode: PowerMode? = null,
    val inferenceDelegate: InferenceDelegate = InferenceDelegate.AUTO,
    val analysisWidth: Int = DEFAULT_ANALYSIS_WIDTH,
    val analysisHeight: Int = DEFAULT_ANALYSIS_HEIGHT,
    val fuseBodyAndHandArm: Boolean = false,
) {
    init {
        require(enableFace || enableHand || enableBody) {
            "At least one tracking modality must be enabled"
        }
        require(analysisWidth > 0) { "analysisWidth must be positive, was $analysisWidth" }
        require(analysisHeight > 0) { "analysisHeight must be positive, was $analysisHeight" }
        require(!fuseBodyAndHandArm || (enableHand && enableBody)) {
            "fuseBodyAndHandArm requires both enableHand and enableBody to be true"
        }
        warnOnConflicts()
    }

    public companion object {
        /** Default camera analysis stream width in pixels. */
        public const val DEFAULT_ANALYSIS_WIDTH: Int = 640

        /** Default camera analysis stream height in pixels. */
        public const val DEFAULT_ANALYSIS_HEIGHT: Int = 480
    }
}

/**
 * Logs warnings when sub-tracker config values conflict with top-level overrides.
 * Called from init — must not reference [this] properties via delegation.
 */
private fun HolisticTrackerConfig.warnOnConflicts() {
    if (enableFace) {
        warnFaceConflicts()
    }
    if (enableHand) {
        warnHandConflicts()
    }
    if (enableBody) {
        warnBodyConflicts()
    }
}

private fun HolisticTrackerConfig.warnFaceConflicts() {
    if (faceConfig.cameraFacing != cameraFacing) {
        logConflict("faceConfig.cameraFacing", faceConfig.cameraFacing, cameraFacing)
    }
    if (faceConfig.powerConfig.thermalPolicy != thermalPolicy) {
        logConflict(
            "faceConfig.powerConfig.thermalPolicy",
            faceConfig.powerConfig.thermalPolicy,
            thermalPolicy,
        )
    }
    if (faceConfig.performanceConfig.inferenceDelegate != inferenceDelegate) {
        logConflict(
            "faceConfig.performanceConfig.inferenceDelegate",
            faceConfig.performanceConfig.inferenceDelegate,
            inferenceDelegate,
        )
    }
}

private fun HolisticTrackerConfig.warnHandConflicts() {
    if (handConfig.cameraFacing != cameraFacing) {
        logConflict("handConfig.cameraFacing", handConfig.cameraFacing, cameraFacing)
    }
    if (handConfig.thermalPolicy != thermalPolicy) {
        logConflict("handConfig.thermalPolicy", handConfig.thermalPolicy, thermalPolicy)
    }
    if (handConfig.inferenceDelegate != inferenceDelegate) {
        logConflict(
            "handConfig.inferenceDelegate",
            handConfig.inferenceDelegate,
            inferenceDelegate,
        )
    }
}

private fun HolisticTrackerConfig.warnBodyConflicts() {
    if (bodyConfig.cameraFacing != cameraFacing) {
        logConflict("bodyConfig.cameraFacing", bodyConfig.cameraFacing, cameraFacing)
    }
    if (bodyConfig.thermalPolicy != thermalPolicy) {
        logConflict("bodyConfig.thermalPolicy", bodyConfig.thermalPolicy, thermalPolicy)
    }
    if (bodyConfig.inferenceDelegate != inferenceDelegate) {
        logConflict(
            "bodyConfig.inferenceDelegate",
            bodyConfig.inferenceDelegate,
            inferenceDelegate,
        )
    }
}

private fun logConflict(property: String, subValue: Any, topValue: Any) {
    FaceLinkLogger.w(
        "HolisticTrackerConfig",
        "$property=$subValue conflicts with top-level value=$topValue. " +
            "Top-level value takes precedence.",
    )
}

/**
 * Tracking modality that can be enabled in holistic tracking.
 */
public enum class TrackingModality {
    /** Face blend shapes, head transform, and landmarks */
    FACE,

    /** Hand landmarks and gestures */
    HAND,

    /** Body pose landmarks */
    BODY,
}
