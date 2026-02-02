package io.github.kmpfacelink.util

/**
 * Platform-specific reentrant lock.
 *
 * - Android: [java.util.concurrent.locks.ReentrantLock]
 * - iOS: [platform.Foundation.NSRecursiveLock]
 */
internal expect class PlatformLock() {
    fun lock()
    fun unlock()
}

/**
 * Execute [block] while holding the [PlatformLock].
 */
internal inline fun <T> PlatformLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
