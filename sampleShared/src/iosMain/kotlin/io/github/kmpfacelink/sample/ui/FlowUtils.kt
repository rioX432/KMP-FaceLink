package io.github.kmpfacelink.sample.ui

import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Creates a [MutableStateFlow] for [FaceTrackingData] that can be used from Swift.
 *
 * Swift cannot directly instantiate Kotlin generic classes, so this factory
 * function bridges the gap.
 */
public fun createFaceTrackingDataStateFlow(): MutableStateFlow<FaceTrackingData?> =
    MutableStateFlow(null)

/**
 * Creates a [MutableStateFlow] for [HandTrackingData] that can be used from Swift.
 *
 * Swift cannot directly instantiate Kotlin generic classes, so this factory
 * function bridges the gap.
 */
public fun createHandTrackingDataStateFlow(): MutableStateFlow<HandTrackingData?> =
    MutableStateFlow(null)
