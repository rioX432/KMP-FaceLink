package io.github.kmpfacelink.unity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Handle for cancelling a Flow collection started via [collectFaceTrackingFlow]
 * or [collectHandTrackingFlow].
 */
public interface Cancellable {
    /** Cancel the Flow collection. */
    public fun cancel()
}

internal class CancellableImpl(
    private val scope: CoroutineScope,
) : Cancellable {
    override fun cancel() {
        scope.coroutineContext[Job]?.cancel()
    }
}
