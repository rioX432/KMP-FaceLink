package io.github.kmpfacelink.model

/**
 * ARKit-compatible 52 blend shape parameters.
 *
 * Naming follows Apple ARKit ARFaceAnchor.BlendShapeLocation conventions.
 * MediaPipe uses the same naming, with 1:1 mapping for supported shapes.
 *
 * @see <a href="https://developer.apple.com/documentation/arkit/arfaceanchor/blendshapelocation">ARKit BlendShapeLocation</a>
 */
public enum class BlendShape {
    // Eyes (14)
    EYE_BLINK_LEFT,
    EYE_BLINK_RIGHT,
    EYE_LOOK_DOWN_LEFT,
    EYE_LOOK_DOWN_RIGHT,
    EYE_LOOK_IN_LEFT,
    EYE_LOOK_IN_RIGHT,
    EYE_LOOK_OUT_LEFT,
    EYE_LOOK_OUT_RIGHT,
    EYE_LOOK_UP_LEFT,
    EYE_LOOK_UP_RIGHT,
    EYE_SQUINT_LEFT,
    EYE_SQUINT_RIGHT,
    EYE_WIDE_LEFT,
    EYE_WIDE_RIGHT,

    // Jaw (4)
    JAW_FORWARD,
    JAW_LEFT,
    JAW_OPEN,
    JAW_RIGHT,

    // Mouth (23)
    MOUTH_CLOSE,
    MOUTH_DIMPLE_LEFT,
    MOUTH_DIMPLE_RIGHT,
    MOUTH_FROWN_LEFT,
    MOUTH_FROWN_RIGHT,
    MOUTH_FUNNEL,
    MOUTH_LEFT,
    MOUTH_LOWER_DOWN_LEFT,
    MOUTH_LOWER_DOWN_RIGHT,
    MOUTH_PRESS_LEFT,
    MOUTH_PRESS_RIGHT,
    MOUTH_PUCKER,
    MOUTH_RIGHT,
    MOUTH_ROLL_LOWER,
    MOUTH_ROLL_UPPER,
    MOUTH_SHRUG_LOWER,
    MOUTH_SHRUG_UPPER,
    MOUTH_SMILE_LEFT,
    MOUTH_SMILE_RIGHT,
    MOUTH_STRETCH_LEFT,
    MOUTH_STRETCH_RIGHT,
    MOUTH_UPPER_UP_LEFT,
    MOUTH_UPPER_UP_RIGHT,

    // Brow (5)
    BROW_DOWN_LEFT,
    BROW_DOWN_RIGHT,
    BROW_INNER_UP,
    BROW_OUTER_UP_LEFT,
    BROW_OUTER_UP_RIGHT,

    // Cheek (3)
    CHEEK_PUFF,
    CHEEK_SQUINT_LEFT,
    CHEEK_SQUINT_RIGHT,

    // Nose (2)
    NOSE_SNEER_LEFT,
    NOSE_SNEER_RIGHT,

    // Tongue (1)
    TONGUE_OUT;

    /**
     * Returns the ARKit-compatible camelCase name.
     * e.g. EYE_BLINK_LEFT → "eyeBlinkLeft"
     */
    public val arKitName: String
        get() = arKitNames.getValue(this)

    public companion object {
        private val arKitNames: Map<BlendShape, String> by lazy {
            entries.associateWith { entry ->
                val parts = entry.name.lowercase().split("_")
                parts.first() + parts.drop(1).joinToString("") { part ->
                    part.replaceFirstChar { it.uppercase() }
                }
            }
        }

        private val arKitNameMap: Map<String, BlendShape> by lazy {
            arKitNames.entries.associate { (k, v) -> v to k }
        }

        /**
         * Look up a BlendShape by its ARKit camelCase name.
         * Returns null if no match is found.
         */
        public fun fromArKitName(name: String): BlendShape? = arKitNameMap[name]
    }
}
