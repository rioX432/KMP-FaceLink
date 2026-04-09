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

    @Test
    fun isArm64HostReturnsBooleanWithoutCrashing() {
        // Verifies the function reads os.arch without throwing.
        // Actual value depends on the host running the test.
        val result = OnnxModelLoader.isArm64Host()
        val arch = System.getProperty("os.arch") ?: ""
        val expected = arch.equals("aarch64", ignoreCase = true) ||
            arch.equals("arm64", ignoreCase = true)
        assertTrue(
            result == expected,
            "isArm64Host()=$result but os.arch=$arch (expected=$expected)",
        )
    }

    companion object {
        private const val MIN_THREADS = 2
        private const val MAX_THREADS = 4
    }
}
