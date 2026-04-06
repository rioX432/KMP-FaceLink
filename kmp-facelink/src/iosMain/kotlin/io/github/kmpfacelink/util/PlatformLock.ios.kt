package io.github.kmpfacelink.util

import platform.Foundation.NSRecursiveLock

public actual class PlatformLock public actual constructor() {
    private val lock = NSRecursiveLock()

    public actual fun lock(): Unit = lock.lock()
    public actual fun unlock(): Unit = lock.unlock()
}
