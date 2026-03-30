package io.github.kmpfacelink.util

/**
 * Returns an opaque identifier for the currently executing thread.
 *
 * The returned value is only meaningful for equality comparison within the same process.
 * Platform implementations:
 * - Android / JVM: [Thread.getId]
 * - iOS: [platform.Foundation.NSThread.hash]
 */
internal expect fun currentThreadId(): Long
