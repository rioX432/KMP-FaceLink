package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class IncompleteTakeTransactionTest {
    @Test
    fun initializationFailureDiscardsTheDirectory() {
        val discarded = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            withIncompleteTakeDirectory(
                create = { "tmp/take" },
                discard = discarded::add,
            ) {
                error("sink open failed")
            }
        }

        assertEquals(listOf("tmp/take"), discarded)
    }

    @Test
    fun successfulOwnershipTransferDoesNotDiscard() {
        val discarded = mutableListOf<String>()

        val result = withIncompleteTakeDirectory(
            create = { "tmp/take" },
            discard = discarded::add,
        ) { "active:$it" }

        assertEquals("active:tmp/take", result)
        assertEquals(emptyList(), discarded)
    }

    @Test
    fun cleanupFailureStaysAttachedToThePrimaryFailure() {
        val primary = captureFailure(CaptureFailureCategory.STREAM_WRITE)
        val cleanup = captureFailure(CaptureFailureCategory.CLEANUP)

        val thrown = assertFailsWith<CaptureFailureException> {
            cleanupIncompleteTakeOnFailure(
                directory = "tmp/take",
                discard = { throw cleanup },
            ) {
                throw primary
            }
        }

        assertSame(primary, thrown)
        assertEquals(listOf(cleanup), thrown.suppressedExceptions)
        assertEquals(CaptureFailureCategory.STREAM_WRITE.receipt(), thrown.toCaptureFailureReceipt())
    }
}
