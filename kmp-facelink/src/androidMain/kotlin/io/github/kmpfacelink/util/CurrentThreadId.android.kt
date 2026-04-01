package io.github.kmpfacelink.util

// Thread.getId() was deprecated in Java 19 in favor of Thread.threadId().
// Android minSdk 24 does not guarantee Java 19+ APIs, so getId() remains correct here.
// Replace with Thread.currentThread().threadId() when minSdk supports Java 19+.
@Suppress("DEPRECATION")
internal actual fun currentThreadId(): Long = Thread.currentThread().id
