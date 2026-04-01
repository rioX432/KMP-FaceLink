package io.github.kmpfacelink.util

/**
 * Platform-agnostic logger for kmp-facelink.
 *
 * - Android: delegates to [android.util.Log]
 * - iOS: delegates to [platform.Foundation.NSLog]
 * - JVM: delegates to [java.util.logging.Logger]
 */
public expect object FaceLinkLogger {
    public fun w(tag: String, message: String, throwable: Throwable? = null)
    public fun e(tag: String, message: String, throwable: Throwable? = null)
    public fun d(tag: String, message: String)
}
