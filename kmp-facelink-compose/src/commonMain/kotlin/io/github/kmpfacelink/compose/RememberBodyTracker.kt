package io.github.kmpfacelink.compose

import androidx.compose.runtime.Composable
import io.github.kmpfacelink.api.BodyTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createBodyTracker
import io.github.kmpfacelink.model.BodyTrackerConfig

/**
 * Creates and remembers a [BodyTracker] scoped to the current composition.
 *
 * The tracker is automatically released when this composable leaves the composition.
 * When [config] changes, the previous tracker is released and a new one is created.
 *
 * @param config Body tracking configuration. Used as a `remember` key — changing it
 *   recreates the tracker.
 * @param autoManageLifecycle When `true` (default), the tracker automatically starts
 *   on [Lifecycle.Event.ON_START][androidx.lifecycle.Lifecycle.Event.ON_START] and stops
 *   on [Lifecycle.Event.ON_STOP][androidx.lifecycle.Lifecycle.Event.ON_STOP].
 *   When `false`, the consumer must call [BodyTracker.start] and [BodyTracker.stop] manually.
 *   Auto-lifecycle does **not** retry on [TrackingState.ERROR] to avoid permission-denied loops.
 * @return A [BodyTracker] instance managed by the composition lifecycle.
 */
@Composable
public fun rememberBodyTracker(
    config: BodyTrackerConfig = BodyTrackerConfig(),
    autoManageLifecycle: Boolean = true,
): BodyTracker = rememberManagedTracker(
    config = config,
    autoManageLifecycle = autoManageLifecycle,
    factory = { context -> createBodyTracker(context, config) },
    start = { it.start() },
    stop = { it.stop() },
)
