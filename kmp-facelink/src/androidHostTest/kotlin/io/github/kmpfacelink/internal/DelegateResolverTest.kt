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
    fun resolveAutoReturnsGpuThenCpuFallback() {
        val chain = DelegateResolver.resolve(InferenceDelegate.AUTO)

        assertEquals(listOf(Delegate.GPU, Delegate.CPU), chain)
    }

    @Test
    fun resolveNpuReturnsGpuThenCpuFallback() {
        // NPU is not yet supported by MediaPipe, so it falls back to GPU -> CPU
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
}
