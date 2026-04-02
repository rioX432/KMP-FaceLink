package io.github.kmpfacelink.unity

import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collects a [Flow] of [FaceTrackingData] and invokes the [callback] on each emission.
 *
 * Designed for use from ObjC/C bridge code where direct Flow collection is not possible.
 * Returns a [Cancellable] that can be used to stop collection.
 */
public fun collectFaceTrackingFlow(
    flow: Flow<FaceTrackingData>,
    callback: (FaceTrackingData) -> Unit,
): Cancellable {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch {
        flow.collect { data ->
            callback(data)
        }
    }
    return CancellableImpl(scope)
}

/**
 * Collects a [Flow] of [HandTrackingData] and invokes the [callback] on each emission.
 *
 * Designed for use from ObjC/C bridge code where direct Flow collection is not possible.
 * Returns a [Cancellable] that can be used to stop collection.
 */
public fun collectHandTrackingFlow(
    flow: Flow<HandTrackingData>,
    callback: (HandTrackingData) -> Unit,
): Cancellable {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch {
        flow.collect { data ->
            callback(data)
        }
    }
    return CancellableImpl(scope)
}
