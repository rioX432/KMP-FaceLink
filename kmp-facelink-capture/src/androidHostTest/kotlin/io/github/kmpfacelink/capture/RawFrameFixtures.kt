package io.github.kmpfacelink.capture

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

internal const val WIDTH = 24
internal const val HEIGHT = 32

/**
 * Random pixels, not a flat colour: a constant image survives a lossy, misordered or aliasing
 * writer too, so it cannot tell a correct implementation from a broken one.
 */
internal fun pixels(seed: Int): ByteArray =
    Random(seed).nextBytes(WIDTH * HEIGHT * RawFrameFormat.BYTES_PER_PIXEL)

internal fun frame(
    seq: Int,
    seed: Int = seq,
    phase: CapturePhase = CapturePhase.ACTED,
): RawFrame = RawFrame(
    width = WIDTH,
    height = HEIGHT,
    analysisFrameSeq = seq,
    captureTimestampNs = 1_000_000L * seq,
    inferenceTimestampMs = seq.toLong(),
    phase = phase,
    pixels = pixels(seed),
)

@Suppress("DEPRECATION")
internal fun tempDir(prefix: String): File =
    File.createTempFile("rawframe-$prefix", "").let {
        it.delete()
        it.mkdirs()
        it
    }

/** Fails the way a full disk does: at write, or held back until flush. */
internal class FailingStream(private val failure: Failure) : OutputStream() {

    enum class Failure { ON_WRITE, ON_FLUSH }

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (failure == Failure.ON_WRITE) throw IOException("simulated ENOSPC on write")
    }

    override fun flush() {
        if (failure == Failure.ON_FLUSH) throw IOException("simulated ENOSPC on flush")
    }
}

/**
 * Parks the writer thread inside a write until released, which is how the shutdown paths that
 * depend on a worker that will not finish are reached deterministically.
 */
internal class BlockingStream(
    private val entered: CountDownLatch,
    private val release: CountDownLatch,
) : OutputStream() {

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        entered.countDown()
        release.await()
    }
}

/**
 * Holds publication open until released, then publishes for real, so what the rest of the world can
 * see mid-publication is observable.
 */
internal class HeldMarkerPublisher(
    private val publishing: CountDownLatch,
    private val release: CountDownLatch,
    private val delegate: MarkerPublisher,
) : MarkerPublisher {

    override fun publish(json: String) {
        publishing.countDown()
        release.await()
        delegate.publish(json)
    }
}

/** Waits for [thread] to reach [state], so a two-thread interleaving can be staged. */
internal fun awaitState(thread: Thread, state: Thread.State, timeoutMs: Long = 5_000) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000
    while (thread.state != state) {
        check(System.nanoTime() < deadline) {
            "$thread never reached $state, sat in ${thread.state}"
        }
        Thread.sleep(1)
    }
}
