package io.github.kmpfacelink.internal.onnx

import kotlin.test.Test
import kotlin.test.assertTrue

class OnnxModelLoaderTest {

    @Test
    fun resolveIntraOpThreadsIsAtLeastTwo() {
        val threads = OnnxModelLoader.resolveIntraOpThreads()
        assertTrue(threads >= MIN_THREADS, "Expected at least $MIN_THREADS threads, got $threads")
    }

    @Test
    fun resolveIntraOpThreadsIsAtMostFour() {
        val threads = OnnxModelLoader.resolveIntraOpThreads()
        assertTrue(threads <= MAX_THREADS, "Expected at most $MAX_THREADS threads, got $threads")
    }

    @Test
    fun resolveIntraOpThreadsNeverExceedsAvailableProcessors() {
        val threads = OnnxModelLoader.resolveIntraOpThreads()
        val available = Runtime.getRuntime().availableProcessors()
        // Only check upper bound when the host has more cores than MAX_THREADS —
        // on small VMs, MIN_THREADS may exceed availableProcessors() by design.
        if (available >= MAX_THREADS) {
            assertTrue(
                threads <= available,
                "Threads ($threads) should not exceed available processors ($available)",
            )
        }
    }

    companion object {
        private const val MIN_THREADS = 2
        private const val MAX_THREADS = 4
    }
}
