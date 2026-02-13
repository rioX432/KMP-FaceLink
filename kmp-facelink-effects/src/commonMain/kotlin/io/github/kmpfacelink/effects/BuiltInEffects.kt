package io.github.kmpfacelink.effects

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.HandGesture

private const val SMILE_THRESHOLD = 0.4f
private const val WINK_THRESHOLD = 0.6f
private const val OPEN_PALM_CONFIDENCE = 0.5f
private const val CARTOON_EYES_SCALE = 2.0f

/**
 * Cat ears positioned on the forehead, tracking head rotation.
 */
@ExperimentalFaceLinkApi
public fun catEarsEffect(id: String = "catEars"): Effect = Effect.AnchorEffect(
    id = id,
    anchor = AnchorPoint.FOREHEAD,
    rotationSource = RotationSource.HEAD_TRANSFORM,
)

/**
 * Glasses positioned on the nose bridge, tracking head rotation.
 */
@ExperimentalFaceLinkApi
public fun glassesEffect(id: String = "glasses"): Effect = Effect.AnchorEffect(
    id = id,
    anchor = AnchorPoint.NOSE_BRIDGE,
    rotationSource = RotationSource.HEAD_TRANSFORM,
)

/**
 * Heart particles triggered by smiling. Intensity maps to smile strength.
 */
@ExperimentalFaceLinkApi
public fun smileHeartsEffect(id: String = "smileHearts"): Effect = Effect.ExpressionEffect(
    id = id,
    blendShapes = listOf(BlendShape.MOUTH_SMILE_LEFT, BlendShape.MOUTH_SMILE_RIGHT),
    threshold = SMILE_THRESHOLD,
    mapping = IntensityMapping.Linear,
)

/**
 * Sparkle effect triggered by winking (left eye blink).
 * Uses step mapping: fully on when winking, off otherwise.
 */
@ExperimentalFaceLinkApi
public fun winkSparkleEffect(id: String = "winkSparkle"): Effect = Effect.ExpressionEffect(
    id = id,
    blendShapes = listOf(BlendShape.EYE_BLINK_LEFT),
    threshold = WINK_THRESHOLD,
    mapping = IntensityMapping.Step(WINK_THRESHOLD),
)

/**
 * Particle effect triggered by open palm gesture.
 */
@ExperimentalFaceLinkApi
public fun openPalmParticlesEffect(id: String = "openPalmParticles"): Effect = Effect.HandEffect(
    id = id,
    gesture = HandGesture.OPEN_PALM,
    minConfidence = OPEN_PALM_CONFIDENCE,
)

/**
 * Cartoon eye scale effect. Transforms eye-wide blend shape into a scale multiplier.
 */
@ExperimentalFaceLinkApi
public fun cartoonEyesEffect(id: String = "cartoonEyes"): Effect = Effect.TransformEffect(
    id = id,
    blendShape = BlendShape.EYE_WIDE_LEFT,
    transform = { raw -> 1f + raw * (CARTOON_EYES_SCALE - 1f) },
)
