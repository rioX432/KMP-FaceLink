package io.github.kmpfacelink.util

// Thread.getId() was deprecated in Java 19 in favor of Thread.threadId().
// Use reflection to call threadId() when available (Java 19+),
// falling back to getId() on older JVMs (project currently targets JVM 17).
// Migration: once the JVM target reaches 19+, drop the reflection and the getId()
// fallback and call Thread.currentThread().threadId() directly.
private val threadIdMethod: java.lang.reflect.Method? = try {
    Thread::class.java.getMethod("threadId")
} catch (_: NoSuchMethodException) {
    null
}

internal actual fun currentThreadId(): Long {
    threadIdMethod?.invoke(Thread.currentThread())?.let { return it as Long }
    @Suppress("DEPRECATION")
    return Thread.currentThread().id
}
