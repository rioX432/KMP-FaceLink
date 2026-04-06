package io.github.kmpfacelink.util

import java.util.concurrent.locks.ReentrantLock

public actual class PlatformLock public actual constructor() {
    private val lock = ReentrantLock()

    public actual fun lock(): Unit = lock.lock()
    public actual fun unlock(): Unit = lock.unlock()
}
