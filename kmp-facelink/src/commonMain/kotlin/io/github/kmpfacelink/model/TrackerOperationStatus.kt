package io.github.kmpfacelink.model

/**
 * What happened when a tracker was asked to change its configuration or reset its state.
 *
 * These operations are part of the shared API, but not every estimator implements every one of
 * them. Returning a status rather than [Unit] is what lets a caller tell "your smoothing settings
 * are now in effect" from "this tracker has no smoothing to configure" — a distinction that a
 * silent no-op destroys, leaving the caller to believe a setting was applied when nothing changed.
 *
 * A status is returned rather than an exception thrown because these calls cross into Swift and C,
 * where an exception is awkward to carry and easy to drop. Calling an operation after the tracker
 * has been released stays an [IllegalStateException]: that is a lifecycle error on the caller's
 * side, not a statement about the estimator's abilities.
 */
public enum class TrackerOperationStatus {
    /** The tracker performed the operation. */
    APPLIED,

    /** This tracker does not implement the operation; nothing changed. */
    UNSUPPORTED,
}
