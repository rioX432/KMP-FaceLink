package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.FacePipelinePerformanceEvent
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.withLock

/**
 * Serializes callback work and lets stop/release close admission before taking a snapshot.
 *
 * A callback may take its entry timestamp immediately before calling [inCallback]. If stop wins the
 * lock in between, the callback is rejected and cannot append after the snapshot. If the callback
 * wins, stop waits for its complete block. This closes the pre-lock timestamp/admission race.
 */
internal class CallbackLifecycleBarrier {
    private val lock = PlatformLock()
    private var accepting = false

    fun open() {
        lock.withLock { accepting = true }
    }

    fun <T> inCallback(callbackEnteredNs: Long, block: (Long) -> T): T? = lock.withLock {
        if (!accepting) return@withLock null
        block(callbackEnteredNs)
    }

    fun closeAndDrain() {
        lock.withLock { accepting = false }
    }
}

/** Bounded, lock-protected snapshot whose overflow stays visible through the first sequence. */
internal class FacePipelinePerformanceBuffer(private val capacity: Int) {
    private val lock = PlatformLock()
    private val events = ArrayList<FacePipelinePerformanceEvent>()

    init {
        require(capacity > 0) { "performance event capacity must be positive" }
    }

    fun append(event: FacePipelinePerformanceEvent) {
        lock.withLock {
            if (events.size == capacity) events.removeAt(0)
            events += event
        }
    }

    fun snapshot(): List<FacePipelinePerformanceEvent> = lock.withLock { events.toList() }
}
