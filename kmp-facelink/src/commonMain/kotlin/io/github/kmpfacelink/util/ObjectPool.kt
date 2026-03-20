package io.github.kmpfacelink.util

/**
 * A simple object pool that reuses pre-allocated instances to reduce GC pressure.
 *
 * Not thread-safe — intended for use from a single thread per instance.
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

    /** Acquire an instance from the pool, creating a new one if the pool is empty. */
    fun acquire(): T {
        val obj = pool.removeLastOrNull() ?: return create()
        reset(obj)
        return obj
    }

    /** Return an instance to the pool for future reuse. Discards the object if the pool is full. */
    fun release(obj: T) {
        if (pool.size < maxSize) {
            pool.addLast(obj)
        }
    }

    /** Discard all pooled objects. */
    fun clear() {
        pool.clear()
    }
}
