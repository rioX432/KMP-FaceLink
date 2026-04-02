package io.github.kmpfacelink.model

/**
 * iOS Vision framework hand pose model revision selection.
 *
 * Apple replaced the hand pose detection model in iOS 26 (revision 2) with a smaller,
 * more accurate model that uses less memory and has lower latency. However, the new model
 * returns slightly different joint locations, which may affect gesture classification
 * thresholds calibrated against revision 1.
 *
 * This setting has no effect on Android (MediaPipe).
 *
 * @see HandTrackerConfig.handPoseRevision
 */
public enum class HandPoseRevision {
    /**
     * Automatically select the best revision for the current iOS version.
     * - iOS 26+: revision 2 (new model with improved accuracy and lower latency)
     * - iOS 14–25: revision 1 (original model)
     */
    AUTO,

    /**
     * Force revision 1 (original model, iOS 14+).
     * Use this for backward-compatible behavior or if gesture classifiers are
     * calibrated against revision 1 joint positions.
     */
    V1,

    /**
     * Force revision 2 (new model, iOS 26+).
     * Smaller, more accurate, less memory, lower latency.
     * Joint locations differ from revision 1 — gesture classifiers may need recalibration.
     * Falls back to revision 1 on iOS versions earlier than 26.
     */
    V2,
}
