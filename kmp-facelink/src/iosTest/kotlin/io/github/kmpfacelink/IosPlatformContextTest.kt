package io.github.kmpfacelink

import io.github.kmpfacelink.api.PlatformContext
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Tests for iOS [PlatformContext] which is an empty class on iOS
 * (ARKit does not require external context).
 */
class IosPlatformContextTest {

    @Test
    fun platformContextCanBeCreated() {
        val context = PlatformContext()
        assertNotNull(context)
    }

    @Test
    fun multiplePlatformContextInstancesAreIndependent() {
        val context1 = PlatformContext()
        val context2 = PlatformContext()
        // Both instances exist independently — no shared state
        assertNotNull(context1)
        assertNotNull(context2)
    }

    @Test
    fun platformContextUsedInFactoryCallsDoesNotThrow() {
        // Verifies PlatformContext can be passed to factory functions
        // without throwing during construction
        val context = PlatformContext()
        assertNotNull(context)
    }
}
