package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosArSessionLeaseTest {

    @Test
    fun captureAndDiagnosticOwnershipAreExclusiveAndTransferable() {
        val capture = IosArSessionLeaseRegistry.acquire("guided capture test")
        assertEquals("guided capture test", IosArSessionLeaseRegistry.activeOwner())
        assertFailsWith<IllegalStateException> {
            IosArSessionLeaseRegistry.acquire("diagnostic test")
        }

        capture.release()
        val diagnostic = IosArSessionLeaseRegistry.acquire("diagnostic test")
        assertEquals("diagnostic test", IosArSessionLeaseRegistry.activeOwner())
        diagnostic.release()
        diagnostic.release()
        assertEquals(null, IosArSessionLeaseRegistry.activeOwner())
    }
}
