package io.github.kmpfacelink.util

/**
 * Returns a fixed thread ID for Wasm/JS since it is single-threaded.
 */
internal actual fun currentThreadId(): Long = 1L
