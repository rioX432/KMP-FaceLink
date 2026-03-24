package io.github.kmpfacelink.vts

import io.github.kmpfacelink.model.BlendShape

/**
 * Mapping from [BlendShape] enum values to VTube Studio default parameter names.
 *
 * VTube Studio uses its own naming convention for tracking parameters
 * (e.g., `EyeOpenLeft`, `MouthSmile`) which differs from ARKit's camelCase names
 * (e.g., `eyeBlinkLeft`, `mouthSmileLeft`).
 *
 * Head transform parameters (FaceAngleX/Y/Z, FacePositionX/Y/Z) are handled
 * separately through [VtsHeadParameters].
 */
internal object VtsParameterNames {

    /**
     * Mapping from [BlendShape] to VTS default parameter name.
     *
     * Note: VTS combines some ARKit parameters into single parameters
     * (e.g., left+right smile into MouthSmile). Blendshapes that map to
     * the same VTS parameter are listed but only one value will be used.
     * Parameters without a direct VTS default mapping return null.
     */
    val mapping: Map<BlendShape, String> = mapOf(
        // Eyes
        BlendShape.EYE_BLINK_LEFT to "EyeOpenLeft",
        BlendShape.EYE_BLINK_RIGHT to "EyeOpenRight",
        BlendShape.EYE_LOOK_UP_LEFT to "EyeLeftY",
        BlendShape.EYE_LOOK_DOWN_LEFT to "EyeLeftY",
        BlendShape.EYE_LOOK_IN_LEFT to "EyeLeftX",
        BlendShape.EYE_LOOK_OUT_LEFT to "EyeLeftX",
        BlendShape.EYE_LOOK_UP_RIGHT to "EyeRightY",
        BlendShape.EYE_LOOK_DOWN_RIGHT to "EyeRightY",
        BlendShape.EYE_LOOK_IN_RIGHT to "EyeRightX",
        BlendShape.EYE_LOOK_OUT_RIGHT to "EyeRightX",
        BlendShape.EYE_WIDE_LEFT to "EyeOpenLeft",
        BlendShape.EYE_WIDE_RIGHT to "EyeOpenRight",

        // Brows
        BlendShape.BROW_DOWN_LEFT to "BrowLeftY",
        BlendShape.BROW_DOWN_RIGHT to "BrowRightY",
        BlendShape.BROW_INNER_UP to "BrowLeftY",
        BlendShape.BROW_OUTER_UP_LEFT to "BrowLeftY",
        BlendShape.BROW_OUTER_UP_RIGHT to "BrowRightY",

        // Mouth
        BlendShape.MOUTH_SMILE_LEFT to "MouthSmile",
        BlendShape.MOUTH_SMILE_RIGHT to "MouthSmile",
        BlendShape.JAW_OPEN to "MouthOpen",
        BlendShape.MOUTH_FUNNEL to "MouthOpen",
        BlendShape.MOUTH_FROWN_LEFT to "MouthSmile",
        BlendShape.MOUTH_FROWN_RIGHT to "MouthSmile",

        // Cheek
        BlendShape.CHEEK_PUFF to "CheekPuff",

        // Tongue
        BlendShape.TONGUE_OUT to "TongueOut",
    )
}

/**
 * VTube Studio head transform parameter names.
 *
 * These parameters control the Live2D model's head rotation and position.
 * Values are in degrees for angles and arbitrary units for position.
 */
internal object VtsHeadParameters {
    const val FACE_ANGLE_X: String = "FaceAngleX"
    const val FACE_ANGLE_Y: String = "FaceAngleY"
    const val FACE_ANGLE_Z: String = "FaceAngleZ"
    const val FACE_POSITION_X: String = "FacePositionX"
    const val FACE_POSITION_Y: String = "FacePositionY"
    const val FACE_POSITION_Z: String = "FacePositionZ"
}
