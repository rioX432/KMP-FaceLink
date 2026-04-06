package io.github.kmpfacelink.util

/**
 * Platform-specific reentrant lock.
 *
 * - Android: [java.util.concurrent.locks.ReentrantLock]
 * - iOS: [platform.Foundation.NSRecursiveLock]
 */
public expect class PlatformLock() {
    public fun lock()
    public fun unlock()
}

/**
 * Execute [block] while holding the [PlatformLock].
 */
public inline fun <T> PlatformLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
