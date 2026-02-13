package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlin.jvm.JvmName

/**
 * Feeds each [FaceTrackingData] frame into the [ActionSystem] as a side-effect.
 * The data flows through unchanged, so this can be chained with other operators
 * (e.g., avatar parameter mapping).
 */
@JvmName("feedFaceActions")
public fun Flow<FaceTrackingData>.feedActions(
    system: ActionSystem,
): Flow<FaceTrackingData> = onEach { system.processFace(it) }

/**
 * Feeds each [HandTrackingData] frame into the [ActionSystem] as a side-effect.
 * The data flows through unchanged.
 */
@JvmName("feedHandActions")
public fun Flow<HandTrackingData>.feedActions(
    system: ActionSystem,
): Flow<HandTrackingData> = onEach { system.processHand(it) }
