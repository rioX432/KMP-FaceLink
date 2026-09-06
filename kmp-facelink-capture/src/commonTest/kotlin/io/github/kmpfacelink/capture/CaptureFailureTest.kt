package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureFailureTest {
    @Test
    fun everyCategoryHasStableUniqueSafeContract() {
        val receipts = CaptureFailureCategory.entries.map(CaptureFailureCategory::receipt)
        assertEquals(receipts.size, receipts.map { it.code }.toSet().size)
        receipts.forEach { receipt ->
            assertTrue(receipt.code.matches(Regex("[a-z_]+")))
            assertFalse(receipt.participantMessageJa.contains('/'))
            assertFalse(receipt.participantMessageJa.contains("Exception"))
        }
    }

    @Test
    fun typedFailureRoundTripsWithoutRawCauseText() {
        val privatePath = "/private/participant/capture/take.bin"
        CaptureFailureCategory.entries.forEach { category ->
            val cause = IllegalStateException("could not write $privatePath")
            val failure = captureFailure(category, cause)
            assertEquals(category.receipt(), failure.toCaptureFailureReceipt())
            assertEquals(category.code, failure.message)
            assertFalse(failure.message.orEmpty().contains(privatePath))
        }
    }

    @Test
    fun wrappedTypedFailureRetainsCategory() {
        val failure = IllegalStateException(
            "platform wrapper",
            captureFailure(CaptureFailureCategory.ROOT_IN_USE),
        )
        assertEquals("root_in_use", failure.toCaptureFailureReceipt().code)
    }

    @Test
    fun suppressedCleanupDoesNotOverwritePrimaryCategory() {
        val primary = captureFailure(CaptureFailureCategory.STREAM_WRITE)
        primary.addSuppressed(captureFailure(CaptureFailureCategory.CLEANUP))
        assertEquals("stream_write", primary.toCaptureFailureReceipt().code)
    }

    @Test
    fun unknownFailureRedactsPathAndMessage() {
        val privatePath = "/private/participant/capture/take.bin"
        val receipt = IllegalStateException("could not write $privatePath").toCaptureFailureReceipt()
        assertEquals(CaptureFailureCategory.UNKNOWN.receipt(), receipt)
        assertFalse(receipt.participantMessageJa.contains(privatePath))
        assertFalse(receipt.participantMessageJa.contains("could not write"))
    }

    @Test
    fun successAndCancellationDoNotInventFailureReceipt() {
        val success = TakeOutcome(CaptureScenario.NEUTRAL, 1, true, "ok")
        val cancelled = TakeOutcome(CaptureScenario.NEUTRAL, 0, false, "take_cancelled")
        assertNull(success.failure)
        assertNull(cancelled.failure)
    }

    @Test
    fun legacyOutcomeErrorOnlyExposesRegisteredCodes() {
        val known = TakeOutcome(CaptureScenario.NEUTRAL, 0, false, "take_failed", "stream_write")
        val legacyRaw = TakeOutcome(
            CaptureScenario.NEUTRAL,
            0,
            false,
            "take_failed",
            "/private/participant/capture/take.bin",
        )
        assertEquals(CaptureFailureCategory.STREAM_WRITE.receipt(), known.failure)
        assertNull(legacyRaw.failure)
    }

    @Test
    fun legacyUiErrorOnlyExposesRegisteredCodes() {
        val known = CaptureUiState(lastError = "stream_write")
        val legacyRaw = CaptureUiState(lastError = "/private/participant/capture/take.bin")
        assertEquals(CaptureFailureCategory.STREAM_WRITE.receipt(), known.lastFailure)
        assertNull(legacyRaw.lastFailure)
    }

    @Test
    fun frameSourceFailureStopsTheRunAndClearsEveryLiveGate() {
        val receipt = CaptureFailureCategory.FRAME_SOURCE.receipt()
        assertFalse(receipt.retryable)
        assertEquals(CaptureFailurePrimaryAction.STOP_RUN, receipt.toPresentation().primaryAction)

        val failed = CaptureUiState(
            gate = HardGate.Result(true, true, true, true, true),
            distanceHint = HardGate.DistanceHint.MOVE_BACK,
            recording = true,
            resultsObserved = 7,
            scriptElapsedMs = 1_000L,
        ).withTerminalCaptureFailure(receipt)

        assertEquals(HardGate.Result(false, false, false, false, false), failed.gate)
        assertEquals(HardGate.DistanceHint.NONE, failed.distanceHint)
        assertEquals(LiveProgress.None, failed.progress)
        assertNull(failed.landmarks)
        assertFalse(failed.recording)
        assertNull(failed.scriptElapsedMs)
        assertEquals("frame_source", failed.lastError)
    }
}
