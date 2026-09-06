package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertFailsWith
import platform.Foundation.NSData

class IosProcessedPerformanceSessionFactoryTest {

    @Test
    fun emptyAuthorizationIsRejectedBeforeAnySessionCanBeConstructed() {
        assertFailsWith<IllegalStateException> {
            IosDiagnosticRunPermitVerifier.verify(NSData())
        }
    }
}
