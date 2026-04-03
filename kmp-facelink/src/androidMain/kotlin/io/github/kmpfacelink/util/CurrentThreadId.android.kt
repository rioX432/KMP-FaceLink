package io.github.kmpfacelink.util

// Thread.getId() was deprecated in Java 19 in favor of Thread.threadId().
// Android added threadId() in API 35 (Android 15).
// Use reflection to call threadId() when available, falling back to getId() on older devices.
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
