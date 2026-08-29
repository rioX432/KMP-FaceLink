package io.github.kmpfacelink.internal

import com.google.mediapipe.tasks.core.Delegate
import io.github.kmpfacelink.model.InferenceDelegate
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DelegateResolverTest {

    // -- resolve() tests --

    @Test
    fun resolveCpuReturnsCpuOnly() {
        val chain = DelegateResolver.resolve(InferenceDelegate.CPU)

        assertEquals(listOf(Delegate.CPU), chain)
    }

    @Test
    fun resolveGpuReturnsGpuThenCpuFallback() {
        val chain = DelegateResolver.resolve(InferenceDelegate.GPU)

        assertEquals(listOf(Delegate.GPU, Delegate.CPU), chain)
    }

    @Test
    fun resolveAutoWithNoNpuReturnsGpuThenCpuFallback() {
        // Under Robolectric, no NPU vendor is detected
        val chain = DelegateResolver.resolve(InferenceDelegate.AUTO)

        assertEquals(listOf(Delegate.GPU, Delegate.CPU), chain)
    }

    @Test
    fun resolveNpuWithNoVendorDetectedFallsBackToGpuCpu() {
        // Under Robolectric, NpuVendorDetector returns NONE (no NPU hardware)
        // so the chain falls back to GPU -> CPU
        val chain = DelegateResolver.resolve(InferenceDelegate.NPU)

        assertEquals(listOf(Delegate.GPU, Delegate.CPU), chain)
    }

    @Test
    fun resolveAlwaysReturnsNonEmptyChain() {
        for (preference in InferenceDelegate.entries) {
            val chain = DelegateResolver.resolve(preference)
            assertTrue(chain.isNotEmpty(), "Chain for $preference should not be empty")
        }
    }

    @Test
    fun resolveChainAlwaysEndsCpu() {
        for (preference in InferenceDelegate.entries) {
            val chain = DelegateResolver.resolve(preference)
            assertEquals(
                Delegate.CPU,
                chain.last(),
                "Chain for $preference should end with CPU",
            )
        }
    }

    // -- createWithFallback() tests --

    @Test
    fun createWithFallbackUsesFirstSuccessfulDelegate() {
        val result = DelegateResolver.createWithFallback(InferenceDelegate.GPU) { delegate ->
            "created-with-${delegate.name}"
        }

        assertEquals("created-with-GPU", result)
    }

    @Test
    fun createWithFallbackFallsBackOnFailure() {
        val result = DelegateResolver.createWithFallback(InferenceDelegate.GPU) { delegate ->
            if (delegate == Delegate.GPU) {
                throw RuntimeException("GPU not available")
            }
            "created-with-${delegate.name}"
        }

        assertEquals("created-with-CPU", result)
    }

    @Test
    fun createResolvedWithFallbackReportsActualDelegate() {
        val resolved = DelegateResolver.createResolvedWithFallback(InferenceDelegate.AUTO) { delegate ->
            if (delegate == Delegate.GPU) throw RuntimeException("GPU not available")
            "created-with-${delegate.name}"
        }

        assertEquals("created-with-CPU", resolved.value)
        assertEquals(Delegate.CPU, resolved.delegate)
    }

    @Test
    fun createWithFallbackThrowsWhenAllDelegatesFail() {
        assertFailsWith<RuntimeException> {
            DelegateResolver.createWithFallback(InferenceDelegate.GPU) { delegate ->
                throw RuntimeException("${delegate.name} failed")
            }
        }
    }

    @Test
    fun createWithFallbackCpuOnlyThrowsOnFailure() {
        val exception = assertFailsWith<RuntimeException> {
            DelegateResolver.createWithFallback(InferenceDelegate.CPU) { _ ->
                throw RuntimeException("CPU failed")
            }
        }

        assertTrue(exception.message?.contains("CPU") == true)
    }

    @Test
    fun createWithFallbackCpuDirectlySucceeds() {
        val result = DelegateResolver.createWithFallback(InferenceDelegate.CPU) { delegate ->
            "created-with-${delegate.name}"
        }

        assertEquals("created-with-CPU", result)
    }

    // -- NPU-specific tests --

    @Test
    fun resolveNpuWithNoVendorChainEndsCpu() {
        // Under Robolectric: no NPU vendor, chain falls back to GPU -> CPU
        val chain = DelegateResolver.resolve(InferenceDelegate.NPU)

        assertEquals(Delegate.CPU, chain.last())
    }

    @Test
    fun resolveAutoWithNoVendorChainDoesNotContainNpu() {
        // Under Robolectric: no NPU detected, AUTO should not include NPU
        val chain = DelegateResolver.resolve(InferenceDelegate.AUTO)

        assertTrue(
            Delegate.NPU !in chain,
            "AUTO chain should not include NPU when no vendor detected",
        )
    }

    @Test
    fun createWithFallbackNpuPreferenceFallsToGpuWhenNoVendor() {
        // Under Robolectric: NPU preference with no vendor detected resolves to GPU -> CPU
        val result = DelegateResolver.createWithFallback(InferenceDelegate.NPU) { delegate ->
            "created-with-${delegate.name}"
        }

        // First in chain is GPU (since no NPU vendor found)
        assertEquals("created-with-GPU", result)
    }

    @Test
    fun createWithFallbackNpuPreferenceGpuFailsFallsToCpu() {
        // NPU preference, no vendor: chain is GPU -> CPU, GPU fails
        val result = DelegateResolver.createWithFallback(InferenceDelegate.NPU) { delegate ->
            if (delegate == Delegate.GPU) {
                throw RuntimeException("GPU not available")
            }
            "created-with-${delegate.name}"
        }

        assertEquals("created-with-CPU", result)
    }

    @Test
    fun createWithFallbackAutoGpuFailsFallsToCpu() {
        // AUTO mode without NPU: chain is GPU -> CPU, GPU fails
        val result = DelegateResolver.createWithFallback(InferenceDelegate.AUTO) { delegate ->
            if (delegate == Delegate.GPU) {
                throw RuntimeException("GPU not available")
            }
            "created-with-${delegate.name}"
        }

        assertEquals("created-with-CPU", result)
    }

    @Test
    fun createWithFallbackNpuPreferenceAllFailThrowsLast() {
        val exception = assertFailsWith<RuntimeException> {
            DelegateResolver.createWithFallback(InferenceDelegate.NPU) { delegate ->
                throw RuntimeException("${delegate.name} failed")
            }
        }

        assertTrue(exception.message?.contains("CPU") == true)
    }
}
