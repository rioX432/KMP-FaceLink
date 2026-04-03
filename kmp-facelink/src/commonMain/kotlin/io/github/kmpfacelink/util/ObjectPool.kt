package io.github.kmpfacelink.util

import kotlin.concurrent.Volatile

/**
 * A simple object pool that reuses pre-allocated instances to reduce GC pressure.
 *
 * Not thread-safe — intended for use from a single thread per instance.
 *
 * When [debugChecksEnabled] is `true`, every [acquire], [release], and [clear] call asserts
 * that it runs on the same thread as the first operation observed, catching accidental
 * cross-thread usage early during development.
 *
 * @param maxSize Maximum number of pooled objects. Extra returned objects are discarded.
 * @param create Factory function to produce new instances when the pool is empty.
 * @param reset Optional function called on each object before it is reused.
 */
internal class ObjectPool<T>(
    private val maxSize: Int = 4,
    private val create: () -> T,
    private val reset: (T) -> Unit = {},
) {
    private val pool = ArrayDeque<T>(maxSize)

    /** Thread ID recorded on first use; [UNINITIALIZED_THREAD_ID] means not yet set. */
    private var ownerThreadId: Long = UNINITIALIZED_THREAD_ID

    /** Validates thread affinity when [debugChecksEnabled] is `true`. */
    private fun checkThread() {
        if (!debugChecksEnabled) return
        val current = currentThreadId()
        if (ownerThreadId == UNINITIALIZED_THREAD_ID) {
            ownerThreadId = current
        } else {
            check(ownerThreadId == current) {
                "ObjectPool accessed from thread $current but was created on thread $ownerThreadId. " +
                    "ObjectPool is not thread-safe."
            }
        }
    }

    /** Acquire an instance from the pool, creating a new one if the pool is empty. */
    fun acquire(): T {
        checkThread()
        val obj = pool.removeLastOrNull() ?: return create()
        reset(obj)
        return obj
    }

    /** Return an instance to the pool for future reuse. Discards the object if the pool is full. */
    fun release(obj: T) {
        checkThread()
        if (pool.size < maxSize) {
            pool.addLast(obj)
        }
    }

    /** Discard all pooled objects. */
    fun clear() {
        checkThread()
        pool.clear()
    }

    internal companion object {
        /** Sentinel value indicating that no thread has used this pool yet. */
        internal const val UNINITIALIZED_THREAD_ID: Long = -1L

        /**
         * Set to `true` to enable runtime thread-affinity assertions on all [ObjectPool] instances.
         *
         * Intended for debug builds only. Defaults to `false` to have no overhead in production.
         */
        @Volatile
        internal var debugChecksEnabled: Boolean = false
    }
}
