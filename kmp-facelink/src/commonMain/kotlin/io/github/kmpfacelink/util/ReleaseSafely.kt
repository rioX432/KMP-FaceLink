package io.github.kmpfacelink.util

import io.github.kmpfacelink.api.Releasable

/**
 * Calls [Releasable.release] inside [runCatching] and logs any failure at warning level.
 *
 * This is the canonical way to release a [Releasable] component during cleanup
 * where a failure should be logged but not propagated. Safe to call on `null`.
 *
 * @param tag Log tag forwarded to [FaceLinkLogger.w].
 * @param componentName Human-readable name used in the log message (e.g. "TtsEngine").
 */
public fun Releasable?.releaseSafely(tag: String, componentName: String) {
    this ?: return
    runCatching { release() }.onFailure {
        FaceLinkLogger.w(tag, "Failed to release $componentName", it)
    }
}
