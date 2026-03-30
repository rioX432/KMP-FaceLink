package io.github.kmpfacelink.avatar

/**
 * VRCFaceTracking Unified Expressions standard.
 *
 * Contains all Base Shapes and Blended Shapes defined by the Unified Expressions
 * specification. Base shapes represent individual anatomical movements, while blended
 * shapes combine multiple base shapes into simpler higher-level expressions.
 *
 * The [parameterName] property returns the VRChat-compatible parameter name used
 * in OSC messages (e.g., `EyeClosedLeft`, `JawOpen`, `MouthSmile`).
 *
 * @see <a href="https://docs.vrcft.io/docs/tutorial-avatars/tutorial-avatars-extras/unified-blendshapes">
 *   Unified Expressions Specification</a>
 */
public enum class UnifiedExpression(
    /** The parameter name used in VRChat OSC addresses. */
    public val parameterName: String,
) {
    // ── Eye Base Shapes ──

    EYE_LOOK_OUT_RIGHT("EyeLookOutRight"),
    EYE_LOOK_IN_RIGHT("EyeLookInRight"),
    EYE_LOOK_UP_RIGHT("EyeLookUpRight"),
    EYE_LOOK_DOWN_RIGHT("EyeLookDownRight"),
    EYE_LOOK_OUT_LEFT("EyeLookOutLeft"),
    EYE_LOOK_IN_LEFT("EyeLookInLeft"),
    EYE_LOOK_UP_LEFT("EyeLookUpLeft"),
    EYE_LOOK_DOWN_LEFT("EyeLookDownLeft"),
    EYE_CLOSED_RIGHT("EyeClosedRight"),
    EYE_CLOSED_LEFT("EyeClosedLeft"),
    EYE_SQUINT_RIGHT("EyeSquintRight"),
    EYE_SQUINT_LEFT("EyeSquintLeft"),
    EYE_WIDE_RIGHT("EyeWideRight"),
    EYE_WIDE_LEFT("EyeWideLeft"),
    EYE_DILATION_RIGHT("EyeDilationRight"),
    EYE_DILATION_LEFT("EyeDilationLeft"),
    EYE_CONSTRICT_RIGHT("EyeConstrictRight"),
    EYE_CONSTRICT_LEFT("EyeConstrictLeft"),

    // ── Brow Base Shapes ──

    BROW_PINCH_RIGHT("BrowPinchRight"),
    BROW_PINCH_LEFT("BrowPinchLeft"),
    BROW_LOWERER_RIGHT("BrowLowererRight"),
    BROW_LOWERER_LEFT("BrowLowererLeft"),
    BROW_INNER_UP_RIGHT("BrowInnerUpRight"),
    BROW_INNER_UP_LEFT("BrowInnerUpLeft"),
    BROW_OUTER_UP_RIGHT("BrowOuterUpRight"),
    BROW_OUTER_UP_LEFT("BrowOuterUpLeft"),

    // ── Nose Base Shapes ──

    NOSE_SNEER_RIGHT("NoseSneerRight"),
    NOSE_SNEER_LEFT("NoseSneerLeft"),
    NASAL_DILATION_RIGHT("NasalDilationRight"),
    NASAL_DILATION_LEFT("NasalDilationLeft"),
    NASAL_CONSTRICT_RIGHT("NasalConstrictRight"),
    NASAL_CONSTRICT_LEFT("NasalConstrictLeft"),

    // ── Cheek Base Shapes ──

    CHEEK_SQUINT_RIGHT("CheekSquintRight"),
    CHEEK_SQUINT_LEFT("CheekSquintLeft"),
    CHEEK_PUFF_RIGHT("CheekPuffRight"),
    CHEEK_PUFF_LEFT("CheekPuffLeft"),
    CHEEK_SUCK_RIGHT("CheekSuckRight"),
    CHEEK_SUCK_LEFT("CheekSuckLeft"),

    // ── Jaw Base Shapes ──

    JAW_OPEN("JawOpen"),
    MOUTH_CLOSED("MouthClosed"),
    JAW_RIGHT("JawRight"),
    JAW_LEFT("JawLeft"),
    JAW_FORWARD("JawForward"),
    JAW_BACKWARD("JawBackward"),
    JAW_CLENCH("JawClench"),
    JAW_MANDIBLE_RAISE("JawMandibleRaise"),

    // ── Lip Base Shapes ──

    LIP_SUCK_UPPER_RIGHT("LipSuckUpperRight"),
    LIP_SUCK_UPPER_LEFT("LipSuckUpperLeft"),
    LIP_SUCK_LOWER_RIGHT("LipSuckLowerRight"),
    LIP_SUCK_LOWER_LEFT("LipSuckLowerLeft"),
    LIP_SUCK_CORNER_RIGHT("LipSuckCornerRight"),
    LIP_SUCK_CORNER_LEFT("LipSuckCornerLeft"),
    LIP_FUNNEL_UPPER_RIGHT("LipFunnelUpperRight"),
    LIP_FUNNEL_UPPER_LEFT("LipFunnelUpperLeft"),
    LIP_FUNNEL_LOWER_RIGHT("LipFunnelLowerRight"),
    LIP_FUNNEL_LOWER_LEFT("LipFunnelLowerLeft"),
    LIP_PUCKER_UPPER_RIGHT("LipPuckerUpperRight"),
    LIP_PUCKER_UPPER_LEFT("LipPuckerUpperLeft"),
    LIP_PUCKER_LOWER_RIGHT("LipPuckerLowerRight"),
    LIP_PUCKER_LOWER_LEFT("LipPuckerLowerLeft"),

    // ── Mouth Base Shapes ──

    MOUTH_UPPER_UP_RIGHT("MouthUpperUpRight"),
    MOUTH_UPPER_UP_LEFT("MouthUpperUpLeft"),
    MOUTH_LOWER_DOWN_RIGHT("MouthLowerDownRight"),
    MOUTH_LOWER_DOWN_LEFT("MouthLowerDownLeft"),
    MOUTH_UPPER_DEEPEN_RIGHT("MouthUpperDeepenRight"),
    MOUTH_UPPER_DEEPEN_LEFT("MouthUpperDeepenLeft"),
    MOUTH_UPPER_RIGHT("MouthUpperRight"),
    MOUTH_UPPER_LEFT("MouthUpperLeft"),
    MOUTH_LOWER_RIGHT("MouthLowerRight"),
    MOUTH_LOWER_LEFT("MouthLowerLeft"),
    MOUTH_CORNER_PULL_RIGHT("MouthCornerPullRight"),
    MOUTH_CORNER_PULL_LEFT("MouthCornerPullLeft"),
    MOUTH_CORNER_SLANT_RIGHT("MouthCornerSlantRight"),
    MOUTH_CORNER_SLANT_LEFT("MouthCornerSlantLeft"),
    MOUTH_FROWN_RIGHT("MouthFrownRight"),
    MOUTH_FROWN_LEFT("MouthFrownLeft"),
    MOUTH_STRETCH_RIGHT("MouthStretchRight"),
    MOUTH_STRETCH_LEFT("MouthStretchLeft"),
    MOUTH_DIMPLE_RIGHT("MouthDimpleRight"),
    MOUTH_DIMPLE_LEFT("MouthDimpleLeft"),
    MOUTH_RAISER_UPPER("MouthRaiserUpper"),
    MOUTH_RAISER_LOWER("MouthRaiserLower"),
    MOUTH_PRESS_RIGHT("MouthPressRight"),
    MOUTH_PRESS_LEFT("MouthPressLeft"),
    MOUTH_TIGHTENER_RIGHT("MouthTightenerRight"),
    MOUTH_TIGHTENER_LEFT("MouthTightenerLeft"),

    // ── Tongue Base Shapes ──

    TONGUE_OUT("TongueOut"),
    TONGUE_UP("TongueUp"),
    TONGUE_DOWN("TongueDown"),
    TONGUE_RIGHT("TongueRight"),
    TONGUE_LEFT("TongueLeft"),
    TONGUE_ROLL("TongueRoll"),
    TONGUE_BEND_DOWN("TongueBendDown"),
    TONGUE_CURL_UP("TongueCurlUp"),
    TONGUE_SQUISH("TongueSquish"),
    TONGUE_FLAT("TongueFlat"),
    TONGUE_TWIST_RIGHT("TongueTwistRight"),
    TONGUE_TWIST_LEFT("TongueTwistLeft"),

    // ── Throat/Neck Base Shapes ──

    SOFT_PALATE_CLOSE("SoftPalateClose"),
    THROAT_SWALLOW("ThroatSwallow"),
    NECK_FLEX_RIGHT("NeckFlexRight"),
    NECK_FLEX_LEFT("NeckFlexLeft"),

    // ── Blended Shapes: Eye ──

    EYE_CLOSED("EyeClosed"),
    EYE_WIDE("EyeWide"),
    EYE_SQUINT("EyeSquint"),
    EYE_DILATION("EyeDilation"),
    EYE_CONSTRICT("EyeConstrict"),

    // ── Blended Shapes: Brow ──

    BROW_DOWN_RIGHT("BrowDownRight"),
    BROW_DOWN_LEFT("BrowDownLeft"),
    BROW_DOWN("BrowDown"),
    BROW_INNER_UP("BrowInnerUp"),
    BROW_UP_RIGHT("BrowUpRight"),
    BROW_UP_LEFT("BrowUpLeft"),
    BROW_UP("BrowUp"),

    // ── Blended Shapes: Nose ──

    NOSE_SNEER("NoseSneer"),
    NASAL_DILATION("NasalDilation"),
    NASAL_CONSTRICT("NasalConstrict"),

    // ── Blended Shapes: Cheek ──

    CHEEK_PUFF("CheekPuff"),
    CHEEK_SUCK("CheekSuck"),
    CHEEK_SQUINT("CheekSquint"),

    // ── Blended Shapes: Lip ──

    LIP_SUCK_UPPER("LipSuckUpper"),
    LIP_SUCK_LOWER("LipSuckLower"),
    LIP_SUCK("LipSuck"),
    LIP_FUNNEL_UPPER("LipFunnelUpper"),
    LIP_FUNNEL_LOWER("LipFunnelLower"),
    LIP_FUNNEL("LipFunnel"),
    LIP_PUCKER_UPPER("LipPuckerUpper"),
    LIP_PUCKER_LOWER("LipPuckerLower"),
    LIP_PUCKER("LipPucker"),

    // ── Blended Shapes: Mouth ──

    MOUTH_UPPER_UP("MouthUpperUp"),
    MOUTH_LOWER_DOWN("MouthLowerDown"),
    MOUTH_OPEN("MouthOpen"),
    MOUTH_RIGHT("MouthRight"),
    MOUTH_LEFT("MouthLeft"),
    MOUTH_SMILE_RIGHT("MouthSmileRight"),
    MOUTH_SMILE_LEFT("MouthSmileLeft"),
    MOUTH_SMILE("MouthSmile"),
    MOUTH_SAD_RIGHT("MouthSadRight"),
    MOUTH_SAD_LEFT("MouthSadLeft"),
    MOUTH_SAD("MouthSad"),
    MOUTH_STRETCH("MouthStretch"),
    MOUTH_DIMPLE("MouthDimple"),
    MOUTH_TIGHTENER("MouthTightener"),
    MOUTH_PRESS("MouthPress"),
    ;

    public companion object {
        private val byParameterName: Map<String, UnifiedExpression> by lazy {
            entries.associateBy { it.parameterName }
        }

        /**
         * Looks up a [UnifiedExpression] by its parameter name.
         *
         * @return the matching expression, or `null` if not found
         */
        public fun fromParameterName(name: String): UnifiedExpression? =
            byParameterName[name]
    }
}
