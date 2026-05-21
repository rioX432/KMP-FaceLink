package io.github.kmpfacelink.compose

import androidx.compose.runtime.Composable
import io.github.kmpfacelink.api.HolisticTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createHolisticTracker
import io.github.kmpfacelink.model.HolisticTrackerConfig

/**
 * Creates and remembers a [HolisticTracker] scoped to the current composition.
 *
 * The tracker is automatically released when this composable leaves the composition.
 * When [config] changes, the previous tracker is released and a new one is created.
 *
 * @param config Holistic tracking configuration. Used as a `remember` key — changing it
 *   recreates the tracker.
 * @param autoManageLifecycle When `true` (default), the tracker automatically starts
 *   on [Lifecycle.Event.ON_START][androidx.lifecycle.Lifecycle.Event.ON_START] and stops
 *   on [Lifecycle.Event.ON_STOP][androidx.lifecycle.Lifecycle.Event.ON_STOP].
 *   When `false`, the consumer must call [HolisticTracker.start] and [HolisticTracker.stop]
 *   manually. Auto-lifecycle does **not** retry on [TrackingState.ERROR] to avoid
 *   permission-denied loops.
 * @return A [HolisticTracker] instance managed by the composition lifecycle.
 */
@Composable
public fun rememberHolisticTracker(
    config: HolisticTrackerConfig = HolisticTrackerConfig(),
    autoManageLifecycle: Boolean = true,
): HolisticTracker = rememberManagedTracker(
    config = config,
    autoManageLifecycle = autoManageLifecycle,
    factory = { context -> createHolisticTracker(context, config) },
    start = { it.start() },
    stop = { it.stop() },
)
