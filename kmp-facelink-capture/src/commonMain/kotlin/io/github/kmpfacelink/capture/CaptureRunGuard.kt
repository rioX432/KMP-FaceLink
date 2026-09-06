package io.github.kmpfacelink.capture

import kotlinx.coroutines.sync.Mutex

/** Prevents two callers from entering gate wait for the same session. */
internal class CaptureRunGuard {
    private val mutex = Mutex()

    fun tryEnter(): Boolean = mutex.tryLock()

    fun leave() {
        mutex.unlock()
    }
}

/** How the fixed runner reached its terminal condition. */
internal enum class ScriptEnd { COMPLETED, STOPPED, FAILED }

internal fun shouldPublishTake(end: ScriptEnd, hasFailure: Boolean): Boolean =
    end == ScriptEnd.COMPLETED && !hasFailure
