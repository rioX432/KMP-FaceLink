package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.FacePipelinePerformanceClock
import io.github.kmpfacelink.model.FacePipelinePerformanceEvent
import io.github.kmpfacelink.model.FacePipelinePerformanceOutcome
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallbackLifecycleBarrierTest {

    @Test
    fun drainWaitsForCallbackThatAlreadyEnteredAndPreservesEntryStamp() {
        val barrier = CallbackLifecycleBarrier()
        barrier.open()
        val inside = CountDownLatch(1)
        val allowReturn = CountDownLatch(1)
        val drained = CountDownLatch(1)
        var observed = -1L

        val callback = thread {
            barrier.inCallback(123L) { entered ->
                observed = entered
                inside.countDown()
                allowReturn.await(2, TimeUnit.SECONDS)
            }
        }
        assertTrue(inside.await(2, TimeUnit.SECONDS))
        val stop = thread {
            barrier.closeAndDrain()
            drained.countDown()
        }
        assertFalse(drained.await(50, TimeUnit.MILLISECONDS))
        allowReturn.countDown()
        callback.join()
        stop.join()
        assertTrue(drained.await(2, TimeUnit.SECONDS))
        assertEquals(123L, observed)
    }

    @Test
    fun callbackTimestampedBeforeStopButAdmittedAfterCloseCannotAppend() {
        val barrier = CallbackLifecycleBarrier()
        barrier.open()
        val timestamped = CountDownLatch(1)
        val mayAttemptAdmission = CountDownLatch(1)
        var appended = false

        val callback = thread {
            val entered = 456L
            timestamped.countDown()
            mayAttemptAdmission.await(2, TimeUnit.SECONDS)
            barrier.inCallback(entered) { appended = true }
        }
        assertTrue(timestamped.await(2, TimeUnit.SECONDS))
        barrier.closeAndDrain()
        mayAttemptAdmission.countDown()
        callback.join()
        assertFalse(appended)

        barrier.open()
        barrier.inCallback(789L) { appended = true }
        assertTrue(appended)
    }

    @Test
    fun boundedBufferMakesOverflowVisibleInSnapshotSequence() {
        val buffer = FacePipelinePerformanceBuffer(capacity = 2)
        buffer.append(drop(1u))
        buffer.append(drop(2u))
        buffer.append(drop(3u))
        assertEquals(listOf(2uL, 3uL), buffer.snapshot().map { it.callbackSequence })
    }

    private fun drop(sequence: ULong) = FacePipelinePerformanceEvent(
        callbackSequence = sequence,
        anchorBatchSequence = sequence,
        clock = FacePipelinePerformanceClock.IOS_MONOTONIC_CALLBACK_CLOCK,
        outcome = FacePipelinePerformanceOutcome.DROPPED_NO_ACTIVE_EPOCH,
        materialization = null,
        configurationFingerprint = null,
        epochFingerprint = null,
        callbackToRawExtractionNs = null,
        rawExtractionToOutputReadyNs = null,
        callbackToOutputReadyNs = null,
        outputReadyToEnqueueReturnNs = null,
        callbackToEnqueueReturnNs = null,
        enqueueAccepted = null,
    )
}
