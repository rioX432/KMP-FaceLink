package io.github.kmpfacelink.util

@Suppress("DEPRECATION")
internal actual fun currentThreadId(): Long = Thread.currentThread().id
