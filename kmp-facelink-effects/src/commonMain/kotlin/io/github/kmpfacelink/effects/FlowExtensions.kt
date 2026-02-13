package io.github.kmpfacelink.effects

import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.jvm.JvmName

/**
 * Feeds each [FaceTrackingData] frame into the [EffectEngine] and emits the resulting [EffectOutput].
 *
 * Unlike `feedActions()` (which passes data through via `onEach`), this transforms each
 * frame into an [EffectOutput] via `map`.
 */
@JvmName("feedFaceEffects")
public fun Flow<FaceTrackingData>.feedEffects(
    engine: EffectEngine,
): Flow<EffectOutput> = map { engine.processFace(it) }

/**
 * Feeds each [HandTrackingData] frame into the [EffectEngine] and emits the resulting [EffectOutput].
 */
@JvmName("feedHandEffects")
public fun Flow<HandTrackingData>.feedEffects(
    engine: EffectEngine,
): Flow<EffectOutput> = map { engine.processHand(it) }
