package io.github.kmpfacelink.util

/**
 * Platform-agnostic logger for kmp-facelink.
 *
 * - Android: delegates to [android.util.Log]
 * - iOS: delegates to [platform.Foundation.NSLog]
 * - JVM: delegates to [java.util.logging.Logger]
 */
internal expect object FaceLinkLogger {
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun d(tag: String, message: String)
}
