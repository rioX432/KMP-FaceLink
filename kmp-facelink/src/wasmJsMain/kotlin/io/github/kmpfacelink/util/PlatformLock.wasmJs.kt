package io.github.kmpfacelink.util

/**
 * Wasm/JS platform lock. Single-threaded environment, so no actual locking is needed.
 */
internal actual class PlatformLock actual constructor() {
    actual fun lock() { /* no-op: single-threaded */ }
    actual fun unlock() { /* no-op: single-threaded */ }
}
