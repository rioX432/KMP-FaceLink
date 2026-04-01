package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform

/**
 * Shared test fixture builders for creating [FaceTrackingData] instances.
 *
 * Eliminates duplicated `trackingData()` helper functions across test files.
 */
internal fun trackingData(
    blendShapes: Map<BlendShape, Float> = emptyMap(),
    headTransform: HeadTransform = HeadTransform(),
    timestampMs: Long = 0L,
    isTracking: Boolean = true,
) = FaceTrackingData(
    blendShapes = blendShapes,
    headTransform = headTransform,
    timestampMs = timestampMs,
    isTracking = isTracking,
)

/**
 * Convenience builder using vararg pairs for concise blend shape specification.
 */
internal fun trackingData(
    vararg blendShapes: Pair<BlendShape, Float>,
    headTransform: HeadTransform = HeadTransform(),
    timestampMs: Long = 0L,
    isTracking: Boolean = true,
) = trackingData(
    blendShapes = blendShapes.toMap(),
    headTransform = headTransform,
    timestampMs = timestampMs,
    isTracking = isTracking,
)

/** Default tolerance for floating-point assertions in avatar mapping tests. */
internal const val DEFAULT_TOLERANCE = 0.01f

/** Stricter tolerance for tests requiring higher precision. */
internal const val STRICT_TOLERANCE = 0.001f
