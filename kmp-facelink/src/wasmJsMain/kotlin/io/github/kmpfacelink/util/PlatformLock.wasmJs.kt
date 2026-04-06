package io.github.kmpfacelink.util

/**
 * Wasm/JS platform lock. Single-threaded environment, so no actual locking is needed.
 */
public actual class PlatformLock public actual constructor() {
    public actual fun lock() { /* no-op: single-threaded */ }
    public actual fun unlock() { /* no-op: single-threaded */ }
}
