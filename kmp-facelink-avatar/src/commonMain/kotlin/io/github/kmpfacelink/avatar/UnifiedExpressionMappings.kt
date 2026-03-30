package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.valueOf

/**
 * Maps ARKit 52 blend shapes to VRCFaceTracking Unified Expressions.
 *
 * Provides direct 1:1 mappings for shapes that have ARKit equivalents, and
 * derived blended shapes that average left/right counterparts. Shapes unique
 * to Unified Expressions (e.g., tongue directions, pupil dilation) that have
 * no ARKit source are not included.
 *
 * @see <a href="https://docs.vrcft.io/docs/tutorial-avatars/tutorial-avatars-extras/compatibility/arkit">
 *   ARKit Compatibility</a>
 */
internal object UnifiedExpressionMappings {

    /**
     * Builds the complete set of mappings from ARKit blend shapes to Unified Expressions.
     *
     * Returns a map keyed by [UnifiedExpression.parameterName] to [ParameterMapping].
     */
    fun buildMappings(): Map<String, ParameterMapping> =
        UnifiedDirectMappings.build() + UnifiedBlendedMappings.build()
}

/** Direct 1:1 ARKit-to-Unified mappings (base shapes). */
internal object UnifiedDirectMappings {

    @Suppress("LongMethod")
    fun build(): Map<String, ParameterMapping> = mapOf(
        // Eye
        direct(UnifiedExpression.EYE_LOOK_OUT_RIGHT, BlendShape.EYE_LOOK_OUT_RIGHT),
        direct(UnifiedExpression.EYE_LOOK_IN_RIGHT, BlendShape.EYE_LOOK_IN_RIGHT),
        direct(UnifiedExpression.EYE_LOOK_UP_RIGHT, BlendShape.EYE_LOOK_UP_RIGHT),
        direct(UnifiedExpression.EYE_LOOK_DOWN_RIGHT, BlendShape.EYE_LOOK_DOWN_RIGHT),
        direct(UnifiedExpression.EYE_LOOK_OUT_LEFT, BlendShape.EYE_LOOK_OUT_LEFT),
        direct(UnifiedExpression.EYE_LOOK_IN_LEFT, BlendShape.EYE_LOOK_IN_LEFT),
        direct(UnifiedExpression.EYE_LOOK_UP_LEFT, BlendShape.EYE_LOOK_UP_LEFT),
        direct(UnifiedExpression.EYE_LOOK_DOWN_LEFT, BlendShape.EYE_LOOK_DOWN_LEFT),
        direct(UnifiedExpression.EYE_CLOSED_RIGHT, BlendShape.EYE_BLINK_RIGHT),
        direct(UnifiedExpression.EYE_CLOSED_LEFT, BlendShape.EYE_BLINK_LEFT),
        direct(UnifiedExpression.EYE_SQUINT_RIGHT, BlendShape.EYE_SQUINT_RIGHT),
        direct(UnifiedExpression.EYE_SQUINT_LEFT, BlendShape.EYE_SQUINT_LEFT),
        direct(UnifiedExpression.EYE_WIDE_RIGHT, BlendShape.EYE_WIDE_RIGHT),
        direct(UnifiedExpression.EYE_WIDE_LEFT, BlendShape.EYE_WIDE_LEFT),
        // Brow
        direct(UnifiedExpression.BROW_DOWN_RIGHT, BlendShape.BROW_DOWN_RIGHT),
        direct(UnifiedExpression.BROW_DOWN_LEFT, BlendShape.BROW_DOWN_LEFT),
        direct(UnifiedExpression.BROW_OUTER_UP_RIGHT, BlendShape.BROW_OUTER_UP_RIGHT),
        direct(UnifiedExpression.BROW_OUTER_UP_LEFT, BlendShape.BROW_OUTER_UP_LEFT),
        // Nose
        direct(UnifiedExpression.NOSE_SNEER_RIGHT, BlendShape.NOSE_SNEER_RIGHT),
        direct(UnifiedExpression.NOSE_SNEER_LEFT, BlendShape.NOSE_SNEER_LEFT),
        // Cheek
        direct(UnifiedExpression.CHEEK_SQUINT_RIGHT, BlendShape.CHEEK_SQUINT_RIGHT),
        direct(UnifiedExpression.CHEEK_SQUINT_LEFT, BlendShape.CHEEK_SQUINT_LEFT),
        // Jaw
        direct(UnifiedExpression.JAW_OPEN, BlendShape.JAW_OPEN),
        direct(UnifiedExpression.MOUTH_CLOSED, BlendShape.MOUTH_CLOSE),
        direct(UnifiedExpression.JAW_RIGHT, BlendShape.JAW_RIGHT),
        direct(UnifiedExpression.JAW_LEFT, BlendShape.JAW_LEFT),
        direct(UnifiedExpression.JAW_FORWARD, BlendShape.JAW_FORWARD),
        // Mouth
        direct(UnifiedExpression.MOUTH_UPPER_UP_RIGHT, BlendShape.MOUTH_UPPER_UP_RIGHT),
        direct(UnifiedExpression.MOUTH_UPPER_UP_LEFT, BlendShape.MOUTH_UPPER_UP_LEFT),
        direct(UnifiedExpression.MOUTH_LOWER_DOWN_RIGHT, BlendShape.MOUTH_LOWER_DOWN_RIGHT),
        direct(UnifiedExpression.MOUTH_LOWER_DOWN_LEFT, BlendShape.MOUTH_LOWER_DOWN_LEFT),
        direct(UnifiedExpression.MOUTH_CORNER_PULL_RIGHT, BlendShape.MOUTH_SMILE_RIGHT),
        direct(UnifiedExpression.MOUTH_CORNER_PULL_LEFT, BlendShape.MOUTH_SMILE_LEFT),
        direct(UnifiedExpression.MOUTH_FROWN_RIGHT, BlendShape.MOUTH_FROWN_RIGHT),
        direct(UnifiedExpression.MOUTH_FROWN_LEFT, BlendShape.MOUTH_FROWN_LEFT),
        direct(UnifiedExpression.MOUTH_STRETCH_RIGHT, BlendShape.MOUTH_STRETCH_RIGHT),
        direct(UnifiedExpression.MOUTH_STRETCH_LEFT, BlendShape.MOUTH_STRETCH_LEFT),
        direct(UnifiedExpression.MOUTH_DIMPLE_RIGHT, BlendShape.MOUTH_DIMPLE_RIGHT),
        direct(UnifiedExpression.MOUTH_DIMPLE_LEFT, BlendShape.MOUTH_DIMPLE_LEFT),
        direct(UnifiedExpression.MOUTH_RAISER_UPPER, BlendShape.MOUTH_SHRUG_UPPER),
        direct(UnifiedExpression.MOUTH_RAISER_LOWER, BlendShape.MOUTH_SHRUG_LOWER),
        direct(UnifiedExpression.MOUTH_PRESS_RIGHT, BlendShape.MOUTH_PRESS_RIGHT),
        direct(UnifiedExpression.MOUTH_PRESS_LEFT, BlendShape.MOUTH_PRESS_LEFT),
        // Tongue
        direct(UnifiedExpression.TONGUE_OUT, BlendShape.TONGUE_OUT),
    )
}

/** Blended (combined) Unified Expression mappings derived from ARKit shapes. */
internal object UnifiedBlendedMappings {

    private const val HALF = 0.5f

    @Suppress("LongMethod")
    fun build(): Map<String, ParameterMapping> = mapOf(
        // Eye blended
        average(UnifiedExpression.EYE_CLOSED, BlendShape.EYE_BLINK_LEFT, BlendShape.EYE_BLINK_RIGHT),
        average(UnifiedExpression.EYE_WIDE, BlendShape.EYE_WIDE_LEFT, BlendShape.EYE_WIDE_RIGHT),
        average(UnifiedExpression.EYE_SQUINT, BlendShape.EYE_SQUINT_LEFT, BlendShape.EYE_SQUINT_RIGHT),
        // Brow blended
        direct(UnifiedExpression.BROW_INNER_UP, BlendShape.BROW_INNER_UP),
        average(UnifiedExpression.BROW_DOWN, BlendShape.BROW_DOWN_LEFT, BlendShape.BROW_DOWN_RIGHT),
        direct(UnifiedExpression.BROW_UP_RIGHT, BlendShape.BROW_OUTER_UP_RIGHT),
        direct(UnifiedExpression.BROW_UP_LEFT, BlendShape.BROW_OUTER_UP_LEFT),
        average(UnifiedExpression.BROW_UP, BlendShape.BROW_OUTER_UP_LEFT, BlendShape.BROW_OUTER_UP_RIGHT),
        // Nose blended
        average(UnifiedExpression.NOSE_SNEER, BlendShape.NOSE_SNEER_LEFT, BlendShape.NOSE_SNEER_RIGHT),
        // Cheek blended
        direct(UnifiedExpression.CHEEK_PUFF_LEFT, BlendShape.CHEEK_PUFF),
        direct(UnifiedExpression.CHEEK_PUFF_RIGHT, BlendShape.CHEEK_PUFF),
        direct(UnifiedExpression.CHEEK_PUFF, BlendShape.CHEEK_PUFF),
        average(UnifiedExpression.CHEEK_SQUINT, BlendShape.CHEEK_SQUINT_LEFT, BlendShape.CHEEK_SQUINT_RIGHT),
        // Lip blended
        direct(UnifiedExpression.LIP_SUCK_UPPER, BlendShape.MOUTH_ROLL_UPPER),
        direct(UnifiedExpression.LIP_SUCK_LOWER, BlendShape.MOUTH_ROLL_LOWER),
        lipSuck(),
        direct(UnifiedExpression.LIP_FUNNEL_UPPER, BlendShape.MOUTH_FUNNEL),
        direct(UnifiedExpression.LIP_FUNNEL_LOWER, BlendShape.MOUTH_FUNNEL),
        direct(UnifiedExpression.LIP_FUNNEL, BlendShape.MOUTH_FUNNEL),
        direct(UnifiedExpression.LIP_PUCKER_UPPER, BlendShape.MOUTH_PUCKER),
        direct(UnifiedExpression.LIP_PUCKER_LOWER, BlendShape.MOUTH_PUCKER),
        direct(UnifiedExpression.LIP_PUCKER, BlendShape.MOUTH_PUCKER),
        // Mouth blended
        average(UnifiedExpression.MOUTH_UPPER_UP, BlendShape.MOUTH_UPPER_UP_LEFT, BlendShape.MOUTH_UPPER_UP_RIGHT),
        average(
            UnifiedExpression.MOUTH_LOWER_DOWN,
            BlendShape.MOUTH_LOWER_DOWN_LEFT,
            BlendShape.MOUTH_LOWER_DOWN_RIGHT,
        ),
        mouthOpen(),
        direct(UnifiedExpression.MOUTH_SMILE_RIGHT, BlendShape.MOUTH_SMILE_RIGHT),
        direct(UnifiedExpression.MOUTH_SMILE_LEFT, BlendShape.MOUTH_SMILE_LEFT),
        average(UnifiedExpression.MOUTH_SMILE, BlendShape.MOUTH_SMILE_LEFT, BlendShape.MOUTH_SMILE_RIGHT),
        direct(UnifiedExpression.MOUTH_SAD_RIGHT, BlendShape.MOUTH_FROWN_RIGHT),
        direct(UnifiedExpression.MOUTH_SAD_LEFT, BlendShape.MOUTH_FROWN_LEFT),
        average(UnifiedExpression.MOUTH_SAD, BlendShape.MOUTH_FROWN_LEFT, BlendShape.MOUTH_FROWN_RIGHT),
        average(UnifiedExpression.MOUTH_STRETCH, BlendShape.MOUTH_STRETCH_LEFT, BlendShape.MOUTH_STRETCH_RIGHT),
        average(UnifiedExpression.MOUTH_DIMPLE, BlendShape.MOUTH_DIMPLE_LEFT, BlendShape.MOUTH_DIMPLE_RIGHT),
        average(UnifiedExpression.MOUTH_PRESS, BlendShape.MOUTH_PRESS_LEFT, BlendShape.MOUTH_PRESS_RIGHT),
        direct(UnifiedExpression.MOUTH_RIGHT, BlendShape.MOUTH_RIGHT),
        direct(UnifiedExpression.MOUTH_LEFT, BlendShape.MOUTH_LEFT),
    )

    private fun lipSuck(): Pair<String, ParameterMapping> =
        UnifiedExpression.LIP_SUCK.parameterName to ParameterMapping.Custom { data ->
            val bs = data.blendShapes
            val upper = bs.valueOf(BlendShape.MOUTH_ROLL_UPPER)
            val lower = bs.valueOf(BlendShape.MOUTH_ROLL_LOWER)
            ((upper + lower) * HALF).coerceIn(0f, 1f)
        }

    private fun mouthOpen(): Pair<String, ParameterMapping> =
        UnifiedExpression.MOUTH_OPEN.parameterName to ParameterMapping.Custom { data ->
            val bs = data.blendShapes
            val jaw = bs.valueOf(BlendShape.JAW_OPEN)
            val closed = bs.valueOf(BlendShape.MOUTH_CLOSE)
            (jaw - closed).coerceIn(0f, 1f)
        }

    private fun average(
        expression: UnifiedExpression,
        left: BlendShape,
        right: BlendShape,
    ): Pair<String, ParameterMapping> =
        expression.parameterName to ParameterMapping.Custom { data ->
            val bs = data.blendShapes
            ((bs.valueOf(left) + bs.valueOf(right)) * HALF).coerceIn(0f, 1f)
        }
}

/** Creates a direct pass-through mapping pair. */
internal fun direct(
    expression: UnifiedExpression,
    blendShape: BlendShape,
): Pair<String, ParameterMapping> =
    expression.parameterName to ParameterMapping.Direct(blendShape)
