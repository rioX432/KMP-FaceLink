package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.TrackingContextEpoch

/** Immutable runtime objects captured for one MediaPipe LIVE_STREAM submission. */
internal data class SubmittedFrameContext(
    val epoch: TrackingContextEpoch,
    val resultProcessor: FaceResultProcessor,
)

/** Atomic timestamp-to-submission-context correlation for MediaPipe LIVE_STREAM callbacks. */
internal class SubmittedFrameContextStore {
    private val contexts = mutableMapOf<Long, SubmittedFrameContext>()

    @Synchronized
    fun record(timestampMs: Long, context: SubmittedFrameContext) {
        check(contexts.put(timestampMs, context) == null) { "Duplicate in-flight timestamp: $timestampMs" }
    }

    @Synchronized
    fun take(timestampMs: Long): SubmittedFrameContext? = contexts.remove(timestampMs)

    @Synchronized
    fun discard(timestampMs: Long) {
        contexts.remove(timestampMs)
    }

    @Synchronized
    fun clear() {
        contexts.clear()
    }

    @Synchronized
    fun size(): Int = contexts.size
}
