package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosCaptureRootLeaseTest {
    @Test
    fun secondSessionForTheSameRootFailsUntilRelease() {
        val root = iosTestDirectory("capture-root-lease")
        try {
            val first = acquireCaptureRootLease(root)
            try {
                val failure = assertFailsWith<CaptureFailureException> { acquireCaptureRootLease(root) }
                assertEquals(CaptureFailureCategory.ROOT_IN_USE, failure.category)
            } finally {
                first.release()
            }
            acquireCaptureRootLease(root).release()
        } finally {
            removeCapturePath(root)
        }
    }
}
