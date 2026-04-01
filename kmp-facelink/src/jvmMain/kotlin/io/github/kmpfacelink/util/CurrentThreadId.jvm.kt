package io.github.kmpfacelink.util

// Thread.getId() was deprecated in Java 19 in favor of Thread.threadId().
// This project targets JVM 17, so threadId() is not available.
// Replace with Thread.currentThread().threadId() when JVM target is bumped to 19+.
@Suppress("DEPRECATION")
internal actual fun currentThreadId(): Long = Thread.currentThread().id
