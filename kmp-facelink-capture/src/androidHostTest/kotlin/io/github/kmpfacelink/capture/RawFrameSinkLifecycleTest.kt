@file:Suppress(
    "FunctionNaming", // Backtick names state the corruption or concurrency property under test.
    "MagicNumber", // Fixture values deliberately make offsets, sizes, and timestamps distinct.
    "TooManyFunctions", // One lifecycle suite covers every publish-last failure boundary.
)

package io.github.kmpfacelink.capture

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every way this layer can lie about a take.
 *
 * A crash is recoverable; a take reported complete when it is not becomes numbers in a result table
 * weeks later, with nothing left to notice it by. So the assertions here are always the pair: what
 * the caller was told, and whether the completion marker exists.
 */
class RawFrameSinkLifecycleTest {

    @Test
    fun `every frame whose write returned normally is in the finished take`() {
        // Accepted means stored. Admission and shutdown are two threads meeting, so this drives
        // them at each other and checks the stored identities against the writes that were told
        // they had succeeded.
        var completed = 0
        var accepted = 0
        repeat(CLOSE_RACE_ROUNDS) { round ->
            val dir = tempDir("close-race-$round")
            val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
            val count = AtomicInteger()
            val writer = Thread {
                var seq = 0
                while (true) {
                    runCatching { sink.write(frame(seq++, seed = 0)) }.onFailure { return@Thread }
                    count.incrementAndGet()
                    // Throttled so the bounded queue does not overflow: an invalidated take leaves
                    // through a different path and would give this invariant nothing to check.
                    LockSupport.parkNanos(150_000)
                }
            }
            writer.start()
            Thread.sleep(1)
            val closedCleanly = runCatching { sink.close() }.isSuccess
            writer.join()

            if (closedCleanly) {
                completed++
                accepted += count.get()
                val stored = RawFrameReader(dir.absolutePath).frames().map { it.analysisFrameSeq }
                assertEquals(
                    List(count.get()) { it },
                    stored.toList(),
                    "round $round: a write() returned success but its frame is not in the take",
                )
            }
            dir.deleteRecursively()
        }
        // Guards the test itself: if overflow or an early exception took every round, the assertion
        // above never ran and this would pass while checking nothing.
        assertTrue(completed > CLOSE_RACE_ROUNDS / 2, "only $completed of $CLOSE_RACE_ROUNDS rounds reached close()")
        assertTrue(accepted > completed, "the rounds accepted $accepted frames in total, too few to race")
    }

    @Test
    fun `a frame offered after close is refused, never quietly accepted`() {
        val dir = tempDir("after-close")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(frame(0))
        sink.close()

        val failure = runCatching { sink.write(frame(1)) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "got $failure")
        assertEquals(1, RawFrameReader(dir.absolutePath).frameCount)
        dir.deleteRecursively()
    }

    @Test
    fun `a payload that cannot be flushed fails the take instead of completing it`() {
        // Disk full shows up at flush, not at write: the frames sat in a 1 MB buffer. Swallowing it
        // meant close() returned normally and stamped a completion marker on a take whose pixels
        // never reached the disk.
        val dir = tempDir("flush-fails")
        val sink = sinkWriting(dir, FailingStream(FailingStream.Failure.ON_FLUSH))
        sink.write(frame(0))

        val failure = runCatching { sink.close() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "close() accepted an unflushable payload: $failure")
        assertNoMarker(dir, "a completion marker was written for a take that never reached the disk")
        dir.deleteRecursively()
    }

    @Test
    fun `a frame that could not be written fails the take at close`() {
        val dir = tempDir("write-fails")
        val sink = sinkWriting(dir, FailingStream(FailingStream.Failure.ON_WRITE))
        sink.write(frame(0))

        val failure = runCatching { sink.close() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "got $failure")
        assertNoMarker(dir)
        dir.deleteRecursively()
    }

    @Test
    fun `both streams report their failure, not only whichever failed first`() {
        // A disk that fails the payload flush usually fails the index flush too, and keeping only
        // the first report is what makes those two indistinguishable afterwards.
        val dir = tempDir("two-failures")
        val sink = sinkWriting(
            dir,
            FailingStream(FailingStream.Failure.ON_FLUSH),
            FailingStream(FailingStream.Failure.ON_FLUSH),
        )
        sink.write(frame(0))

        val failure = runCatching { sink.close() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "got $failure")
        val reported = requireNotNull(failure.cause) { "no cause on $failure" }
        assertTrue(
            reported.suppressed.isNotEmpty(),
            "only ${reported.message} survived; the other stream's failure was discarded",
        )
        dir.deleteRecursively()
    }

    @Test
    fun `close reports the same verdict however many times it is called`() {
        // Two callers must not disagree about whether a take completed. The earlier version
        // returned normally from every close() after the first, so whoever closed second was told
        // the failed take was fine.
        val broken = tempDir("idempotent-broken")
        val failing = sinkWriting(broken, FailingStream(FailingStream.Failure.ON_FLUSH))
        failing.write(frame(0))
        val first = runCatching { failing.close() }.exceptionOrNull()
        val second = runCatching { failing.close() }.exceptionOrNull()
        assertTrue(first is IllegalStateException, "first close: $first")
        assertTrue(second is IllegalStateException, "second close() called the failed take complete")

        val whole = tempDir("idempotent-whole")
        val good = openRawFrameSink(whole.absolutePath, WIDTH, HEIGHT)
        good.write(frame(0))
        good.close()
        good.close()
        assertEquals(1, RawFrameReader(whole.absolutePath).frameCount)

        broken.deleteRecursively()
        whole.deleteRecursively()
    }

    @Test
    fun `an interrupted closer does not let the closer behind it call the take complete`() {
        // The owning closer can leave through an exception rather than through its own failure
        // bookkeeping: join() throws when the closing thread is interrupted. The phase still goes
        // terminal, so without recording that escape the waiting closer read "no failure" and
        // reported success for a take that was never sealed.
        val dir = tempDir("interrupted-closer")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sink = sinkWriting(dir, BlockingStream(entered, release))
        sink.write(frame(0))
        assertTrue(entered.await(5, TimeUnit.SECONDS), "the writer never reached the payload")

        val ownerFailure = AtomicReference<Throwable?>()
        val ownerInterruptKept = AtomicReference(false)
        val owner = Thread {
            ownerFailure.set(runCatching { sink.close() }.exceptionOrNull())
            ownerInterruptKept.set(Thread.currentThread().isInterrupted)
        }
        owner.start()
        awaitState(owner, Thread.State.TIMED_WAITING) // inside worker.join(timeout)

        val waiterFailure = AtomicReference<Throwable?>()
        val waiter = Thread { waiterFailure.set(runCatching { sink.close() }.exceptionOrNull()) }
        waiter.start()
        awaitState(waiter, Thread.State.WAITING) // on the shutdown condition

        owner.interrupt()
        owner.join(5_000)
        waiter.join(5_000)
        release.countDown()

        // Both closers get the take verdict in the same shape, with the interruption as its cause,
        // and the interrupted thread keeps its flag.
        val reported = ownerFailure.get()
        assertTrue(reported is IllegalStateException, "the interrupted closer reported $reported")
        assertTrue(
            reported.cause is InterruptedException,
            "the interruption was lost: ${reported.cause}",
        )
        assertTrue(ownerInterruptKept.get(), "the interrupt flag was swallowed")
        assertTrue(
            waiterFailure.get() is IllegalStateException,
            "the waiting closer was told an unsealed take was complete: ${waiterFailure.get()}",
        )
        assertNoMarker(dir)
        dir.deleteRecursively()
    }

    @Test
    fun `two closers agree that a whole take completed`() {
        val dir = tempDir("two-closers")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        repeat(3) { sink.write(frame(it)) }

        val failures = List(2) { AtomicReference<Throwable?>() }
        val closers = failures.map { slot ->
            Thread { slot.set(runCatching { sink.close() }.exceptionOrNull()) }
        }
        closers.forEach(Thread::start)
        closers.forEach { it.join(5_000) }

        failures.forEachIndexed { i, slot ->
            assertTrue(slot.get() == null, "closer $i failed: ${slot.get()}")
        }
        assertEquals(3, RawFrameReader(dir.absolutePath).frameCount)
        dir.deleteRecursively()
    }

    @Test
    fun `a completion marker that cannot be published leaves nothing behind`() {
        // The marker is written to a staging name and renamed into place, so a reader never meets a
        // half-written one. Being written last is not enough on its own: writeText can fail part
        // way through, and the reader picks fields out of the text rather than parsing whole JSON,
        // so a marker cut off after bytesPerFrame would still read as a complete take.
        val dir = tempDir("marker-unpublishable")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(frame(0))
        assertTrue(dir.setWritable(false), "could not make the directory read-only")

        val failure = runCatching { sink.close() }.exceptionOrNull()
        dir.setWritable(true)

        assertTrue(failure is IllegalStateException, "close() reported success without a marker: $failure")
        assertNoMarker(dir)
        assertEquals(
            emptyList(),
            dir.list().orEmpty().filter { it.endsWith(".partial") },
            "a staging marker was left in the take directory",
        )
        // A publication that failed still gives the directory back, or the retry could not record.
        val retry = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        retry.write(frame(0))
        retry.close()
        assertEquals(1, RawFrameReader(dir.absolutePath).frameCount)
        dir.deleteRecursively()
    }

    @Test
    fun `a completion marker from an earlier take does not survive into the next one`() {
        val dir = tempDir("stale-marker")
        val first = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        repeat(3) { first.write(frame(it)) }
        first.close()
        assertTrue(File(dir, RawFrameFormat.FRAMES_META_FILE).isFile)

        // Re-record into the same directory and abandon it without closing, as a crash would. The
        // payload has already been truncated, so the old marker would be standing over frames that
        // are no longer there.
        openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT).write(frame(0))

        val failure = runCatching { RawFrameReader(dir.absolutePath) }.exceptionOrNull()
        assertTrue(
            failure is IllegalArgumentException,
            "the previous take's marker was still standing over a truncated payload: $failure",
        )
        dir.deleteRecursively()
    }

    @Test
    fun `a marker that cannot be removed refuses the take instead of truncating it`() {
        val dir = tempDir("undeletable-marker")
        val first = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        repeat(2) { first.write(frame(it)) }
        first.close()
        val payloadBytes = File(dir, RawFrameFormat.FRAMES_FILE).length()

        // A non-empty directory in the marker's place cannot be deleted, which is what a marker
        // that will not go looks like from here.
        val marker = File(dir, RawFrameFormat.FRAMES_META_FILE)
        marker.delete()
        marker.mkdirs()
        File(marker, "occupied").writeText("x")

        val failure = runCatching { openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT) }
            .exceptionOrNull()
        assertTrue(failure is IOException, "got $failure")
        assertEquals(
            payloadBytes,
            File(dir, RawFrameFormat.FRAMES_FILE).length(),
            "the payload was truncated even though the take was refused",
        )

        // And the refusal released its claim, so the directory works again once the marker can go.
        marker.deleteRecursively()
        val retry = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        retry.write(frame(0))
        retry.close()
        assertEquals(1, RawFrameReader(dir.absolutePath).frameCount)
        dir.deleteRecursively()
    }

    @Test
    fun `a second sink cannot record into a directory that is already being recorded`() {
        // Two sinks on one directory truncate each other's payload and both report success.
        val dir = tempDir("double-open")
        val first = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)

        val failure = runCatching { openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT) }
            .exceptionOrNull()
        assertTrue(failure is IllegalStateException, "got $failure")

        first.write(frame(0))
        first.close()

        // The claim goes with the shutdown, so the directory is available again.
        val second = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        second.write(frame(0))
        second.close()
        assertEquals(1, RawFrameReader(dir.absolutePath).frameCount)
        dir.deleteRecursively()
    }

    @Test
    fun `the directory stays claimed while the marker is going down`() {
        // Releasing the claim before publication is not observable from the outside once close() has
        // returned, so publication is held open here. If the directory were free at that moment,
        // another take could claim it, truncate the payload, and then have this take's completion
        // marker land on top of its half-written files.
        val dir = tempDir("claim-window")
        val publishing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT) {
            HeldMarkerPublisher(publishing, release, AtomicMarkerPublisher(it))
        }
        sink.write(frame(0))

        val closer = Thread { runCatching { sink.close() } }
        closer.start()
        assertTrue(publishing.await(5, TimeUnit.SECONDS), "publication never started")

        val taken = runCatching { openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT) }
            .exceptionOrNull()
        release.countDown()
        closer.join(5_000)

        assertTrue(
            taken is IllegalStateException,
            "the directory was free while this take's marker was still going down: $taken",
        )
        assertEquals(1, RawFrameReader(dir.absolutePath).frameCount)
        dir.deleteRecursively()
    }

    @Test
    fun `an interruption during publication keeps its shape and the interrupt flag`() {
        // The finalization steps used to be wrapped in runCatching, which swallows the two cases
        // that are not take verdicts: an interruption has to leave the caller's flag restored.
        val dir = tempDir("interrupted-publish")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT) {
            MarkerPublisher { throw InterruptedException("simulated interruption while publishing") }
        }
        sink.write(frame(0))

        val failure = runCatching { sink.close() }.exceptionOrNull()
        val flagKept = Thread.interrupted() // reads and clears, so the test leaves no residue

        assertTrue(failure is IllegalStateException, "got $failure")
        assertTrue(flagKept, "the interrupt flag was swallowed")
        assertNoMarker(dir)
        dir.deleteRecursively()
    }

    @Test
    fun `a marker that cannot be renamed into place leaves no staging file`() {
        val dir = tempDir("marker-rename")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(frame(0))
        // A non-empty directory where the marker goes cannot be replaced by a rename.
        val blocker = File(dir, RawFrameFormat.FRAMES_META_FILE)
        blocker.mkdirs()
        File(blocker, "occupied").writeText("x")

        val failure = runCatching { sink.close() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException, "got $failure")
        assertTrue(blocker.isDirectory, "the blocker was replaced")
        assertEquals(
            emptyList(),
            dir.list().orEmpty().filter { it.endsWith(".partial") },
            "a staging marker was left behind",
        )
        dir.deleteRecursively()
    }

    @Test
    fun `a sink cannot release a claim it never took`() {
        // A sink that only knew the path could hand back an entry another sink was relying on, and
        // then two takes would be recording into one directory again.
        val dir = tempDir("claim-ownership")
        val owner = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)

        val impostor = sinkWriting(dir, ByteArrayOutputStream())
        impostor.write(frame(0))
        impostor.close()

        val failure = runCatching { openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT) }
            .exceptionOrNull()
        assertTrue(failure is IllegalStateException, "the impostor gave away the owner's claim: $failure")

        owner.write(frame(0))
        owner.close()
        dir.deleteRecursively()
    }

    @Test
    fun `a take is refused unless its geometry can hold a frame`() {
        // Otherwise the take opens, every write() is rejected on geometry, and it still closes with
        // a completion marker over zero frames.
        val dir = tempDir("geometry")
        assertTrue(
            runCatching { openRawFrameSink(dir.absolutePath, 0, HEIGHT) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { openRawFrameSink(dir.absolutePath, 40_000, 40_000) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        // Large enough that the byte count overflows if it is computed before being bounded, and
        // the wrapped negative would pass a check on the product.
        assertTrue(
            runCatching { openRawFrameSink(dir.absolutePath, Int.MAX_VALUE, Int.MAX_VALUE) }
                .exceptionOrNull() is IllegalArgumentException,
            "an overflowing geometry was accepted",
        )

        // Refused before anything was claimed, so the directory is still usable.
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(frame(0))
        sink.close()
        assertEquals(1, RawFrameReader(dir.absolutePath).frameCount)
        dir.deleteRecursively()
    }

    /** A sink whose payload stream fails or blocks on demand; the index is kept out of the way. */
    private fun sinkWriting(
        dir: File,
        payload: java.io.OutputStream,
        index: java.io.OutputStream = ByteArrayOutputStream(),
    ): RawFrameSink =
        AndroidRawFrameSink(
            directory = dir,
            claim = NO_DIRECTORY_CLAIM,
            marker = AtomicMarkerPublisher(dir),
            width = WIDTH,
            height = HEIGHT,
            payload = payload,
            index = index,
        )

    private fun assertNoMarker(dir: File, message: String = "an incomplete take was marked complete") {
        assertFalse(File(dir, RawFrameFormat.FRAMES_META_FILE).exists(), message)
    }

    private companion object {
        const val CLOSE_RACE_ROUNDS = 100
    }
}
