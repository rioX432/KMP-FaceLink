package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.model.BlendShape

/**
 * Mapping from [BlendShape] enum values to iFacialMocap protocol names.
 *
 * iFacialMocap uses a naming convention with `_L`/`_R` suffixes
 * (e.g., `eyeBlink_L`, `browDown_R`) instead of ARKit's `Left`/`Right`
 * (e.g., `eyeBlinkLeft`, `browDownRight`).
 */
internal object IfmBlendShapeNames {

    /**
     * Ordered list of all 52 blendshape entries as (BlendShape, ifmName) pairs.
     * The order matches the iFacialMocap protocol convention.
     */
    val entries: List<Pair<BlendShape, String>> = listOf(
        // Eyes
        BlendShape.EYE_LOOK_IN_LEFT to "eyeLookIn_L",
        BlendShape.EYE_LOOK_OUT_LEFT to "eyeLookOut_L",
        BlendShape.EYE_LOOK_DOWN_LEFT to "eyeLookDown_L",
        BlendShape.EYE_LOOK_UP_LEFT to "eyeLookUp_L",
        BlendShape.EYE_BLINK_LEFT to "eyeBlink_L",
        BlendShape.EYE_SQUINT_LEFT to "eyeSquint_L",
        BlendShape.EYE_WIDE_LEFT to "eyeWide_L",
        BlendShape.EYE_LOOK_IN_RIGHT to "eyeLookIn_R",
        BlendShape.EYE_LOOK_OUT_RIGHT to "eyeLookOut_R",
        BlendShape.EYE_LOOK_DOWN_RIGHT to "eyeLookDown_R",
        BlendShape.EYE_LOOK_UP_RIGHT to "eyeLookUp_R",
        BlendShape.EYE_BLINK_RIGHT to "eyeBlink_R",
        BlendShape.EYE_SQUINT_RIGHT to "eyeSquint_R",
        BlendShape.EYE_WIDE_RIGHT to "eyeWide_R",
        // Brows
        BlendShape.BROW_DOWN_LEFT to "browDown_L",
        BlendShape.BROW_OUTER_UP_LEFT to "browOuterUp_L",
        BlendShape.BROW_DOWN_RIGHT to "browDown_R",
        BlendShape.BROW_OUTER_UP_RIGHT to "browOuterUp_R",
        BlendShape.BROW_INNER_UP to "browInnerUp",
        // Nose
        BlendShape.NOSE_SNEER_LEFT to "noseSneer_L",
        BlendShape.NOSE_SNEER_RIGHT to "noseSneer_R",
        // Cheek
        BlendShape.CHEEK_SQUINT_LEFT to "cheekSquint_L",
        BlendShape.CHEEK_SQUINT_RIGHT to "cheekSquint_R",
        BlendShape.CHEEK_PUFF to "cheekPuff",
        // Mouth
        BlendShape.MOUTH_LEFT to "mouthLeft",
        BlendShape.MOUTH_DIMPLE_LEFT to "mouthDimple_L",
        BlendShape.MOUTH_FROWN_LEFT to "mouthFrown_L",
        BlendShape.MOUTH_LOWER_DOWN_LEFT to "mouthLowerDown_L",
        BlendShape.MOUTH_PRESS_LEFT to "mouthPress_L",
        BlendShape.MOUTH_SMILE_LEFT to "mouthSmile_L",
        BlendShape.MOUTH_STRETCH_LEFT to "mouthStretch_L",
        BlendShape.MOUTH_UPPER_UP_LEFT to "mouthUpperUp_L",
        BlendShape.MOUTH_RIGHT to "mouthRight",
        BlendShape.MOUTH_DIMPLE_RIGHT to "mouthDimple_R",
        BlendShape.MOUTH_FROWN_RIGHT to "mouthFrown_R",
        BlendShape.MOUTH_LOWER_DOWN_RIGHT to "mouthLowerDown_R",
        BlendShape.MOUTH_PRESS_RIGHT to "mouthPress_R",
        BlendShape.MOUTH_SMILE_RIGHT to "mouthSmile_R",
        BlendShape.MOUTH_STRETCH_RIGHT to "mouthStretch_R",
        BlendShape.MOUTH_UPPER_UP_RIGHT to "mouthUpperUp_R",
        BlendShape.MOUTH_CLOSE to "mouthClose",
        BlendShape.MOUTH_FUNNEL to "mouthFunnel",
        BlendShape.MOUTH_PUCKER to "mouthPucker",
        BlendShape.MOUTH_ROLL_LOWER to "mouthRollLower",
        BlendShape.MOUTH_ROLL_UPPER to "mouthRollUpper",
        BlendShape.MOUTH_SHRUG_LOWER to "mouthShrugLower",
        BlendShape.MOUTH_SHRUG_UPPER to "mouthShrugUpper",
        // Jaw
        BlendShape.JAW_LEFT to "jawLeft",
        BlendShape.JAW_RIGHT to "jawRight",
        BlendShape.JAW_FORWARD to "jawForward",
        BlendShape.JAW_OPEN to "jawOpen",
        // Tongue
        BlendShape.TONGUE_OUT to "tongueOut",
    )
}
