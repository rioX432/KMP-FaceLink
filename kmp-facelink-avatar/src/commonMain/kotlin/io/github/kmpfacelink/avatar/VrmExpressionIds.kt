package io.github.kmpfacelink.avatar

/**
 * Standard VRM expression IDs for both VRM 0.x and VRM 1.0 specifications.
 *
 * VRM 0.x uses PascalCase (BlendShapeProxy), VRM 1.0 uses camelCase (ExpressionManager).
 */
public object VrmExpressionIds {

    // --- VRM 0.x (PascalCase) ---

    /** VRM 0.x expression IDs for emotions. */
    public const val V0_JOY: String = "Joy"
    public const val V0_ANGRY: String = "Angry"
    public const val V0_SORROW: String = "Sorrow"
    public const val V0_FUN: String = "Fun"
    public const val V0_SURPRISE: String = "Surprise"
    public const val V0_NEUTRAL: String = "Neutral"

    /** VRM 0.x expression IDs for mouth visemes. */
    public const val V0_A: String = "A"
    public const val V0_I: String = "I"
    public const val V0_U: String = "U"
    public const val V0_E: String = "E"
    public const val V0_O: String = "O"

    /** VRM 0.x expression IDs for eyes. */
    public const val V0_BLINK: String = "Blink"
    public const val V0_BLINK_L: String = "BlinkL"
    public const val V0_BLINK_R: String = "BlinkR"

    /** VRM 0.x expression IDs for gaze. */
    public const val V0_LOOK_UP: String = "LookUp"
    public const val V0_LOOK_DOWN: String = "LookDown"
    public const val V0_LOOK_LEFT: String = "LookLeft"
    public const val V0_LOOK_RIGHT: String = "LookRight"

    /** VRM 0.x head bone rotation parameter IDs. */
    public const val V0_HEAD_YAW: String = "HeadYaw"
    public const val V0_HEAD_PITCH: String = "HeadPitch"
    public const val V0_HEAD_ROLL: String = "HeadRoll"

    // --- VRM 1.0 (camelCase) ---

    /** VRM 1.0 expression IDs for emotions. */
    public const val V1_HAPPY: String = "happy"
    public const val V1_ANGRY: String = "angry"
    public const val V1_SAD: String = "sad"
    public const val V1_RELAXED: String = "relaxed"
    public const val V1_SURPRISED: String = "surprised"
    public const val V1_NEUTRAL: String = "neutral"

    /** VRM 1.0 expression IDs for mouth visemes. */
    public const val V1_AA: String = "aa"
    public const val V1_IH: String = "ih"
    public const val V1_OU: String = "ou"
    public const val V1_EE: String = "ee"
    public const val V1_OH: String = "oh"

    /** VRM 1.0 expression IDs for eyes. */
    public const val V1_BLINK: String = "blink"
    public const val V1_BLINK_LEFT: String = "blinkLeft"
    public const val V1_BLINK_RIGHT: String = "blinkRight"

    /** VRM 1.0 expression IDs for gaze. */
    public const val V1_LOOK_UP: String = "lookUp"
    public const val V1_LOOK_DOWN: String = "lookDown"
    public const val V1_LOOK_LEFT: String = "lookLeft"
    public const val V1_LOOK_RIGHT: String = "lookRight"

    /** VRM 1.0 head bone rotation parameter IDs. */
    public const val V1_HEAD_YAW: String = "headYaw"
    public const val V1_HEAD_PITCH: String = "headPitch"
    public const val V1_HEAD_ROLL: String = "headRoll"
}
