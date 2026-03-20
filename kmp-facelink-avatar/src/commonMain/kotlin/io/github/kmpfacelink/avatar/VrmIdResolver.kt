package io.github.kmpfacelink.avatar

/**
 * Resolves version-specific VRM expression IDs.
 *
 * Groups related IDs into data classes for convenient access.
 */
internal object VrmIdResolver {

    fun visemeIds(version: VrmVersion): VisemeIds = when (version) {
        VrmVersion.V0_X -> VisemeIds(
            a = VrmExpressionIds.V0_A,
            i = VrmExpressionIds.V0_I,
            u = VrmExpressionIds.V0_U,
            e = VrmExpressionIds.V0_E,
            o = VrmExpressionIds.V0_O,
        )
        VrmVersion.V1_0 -> VisemeIds(
            a = VrmExpressionIds.V1_AA,
            i = VrmExpressionIds.V1_IH,
            u = VrmExpressionIds.V1_OU,
            e = VrmExpressionIds.V1_EE,
            o = VrmExpressionIds.V1_OH,
        )
    }

    fun emotionIds(version: VrmVersion): EmotionIds = when (version) {
        VrmVersion.V0_X -> EmotionIds(
            joy = VrmExpressionIds.V0_JOY,
            angry = VrmExpressionIds.V0_ANGRY,
            sorrow = VrmExpressionIds.V0_SORROW,
            surprise = VrmExpressionIds.V0_SURPRISE,
            neutral = VrmExpressionIds.V0_NEUTRAL,
            relaxed = VrmExpressionIds.V0_FUN,
        )
        VrmVersion.V1_0 -> EmotionIds(
            joy = VrmExpressionIds.V1_HAPPY,
            angry = VrmExpressionIds.V1_ANGRY,
            sorrow = VrmExpressionIds.V1_SAD,
            surprise = VrmExpressionIds.V1_SURPRISED,
            neutral = VrmExpressionIds.V1_NEUTRAL,
            relaxed = VrmExpressionIds.V1_RELAXED,
        )
    }

    fun blinkIds(version: VrmVersion): BlinkIds = when (version) {
        VrmVersion.V0_X -> BlinkIds(
            both = VrmExpressionIds.V0_BLINK,
            left = VrmExpressionIds.V0_BLINK_L,
            right = VrmExpressionIds.V0_BLINK_R,
        )
        VrmVersion.V1_0 -> BlinkIds(
            both = VrmExpressionIds.V1_BLINK,
            left = VrmExpressionIds.V1_BLINK_LEFT,
            right = VrmExpressionIds.V1_BLINK_RIGHT,
        )
    }

    fun gazeIds(version: VrmVersion): GazeIds = when (version) {
        VrmVersion.V0_X -> GazeIds(
            up = VrmExpressionIds.V0_LOOK_UP,
            down = VrmExpressionIds.V0_LOOK_DOWN,
            left = VrmExpressionIds.V0_LOOK_LEFT,
            right = VrmExpressionIds.V0_LOOK_RIGHT,
        )
        VrmVersion.V1_0 -> GazeIds(
            up = VrmExpressionIds.V1_LOOK_UP,
            down = VrmExpressionIds.V1_LOOK_DOWN,
            left = VrmExpressionIds.V1_LOOK_LEFT,
            right = VrmExpressionIds.V1_LOOK_RIGHT,
        )
    }

    fun headIds(version: VrmVersion): HeadIds = when (version) {
        VrmVersion.V0_X -> HeadIds(
            yaw = VrmExpressionIds.V0_HEAD_YAW,
            pitch = VrmExpressionIds.V0_HEAD_PITCH,
            roll = VrmExpressionIds.V0_HEAD_ROLL,
        )
        VrmVersion.V1_0 -> HeadIds(
            yaw = VrmExpressionIds.V1_HEAD_YAW,
            pitch = VrmExpressionIds.V1_HEAD_PITCH,
            roll = VrmExpressionIds.V1_HEAD_ROLL,
        )
    }
}

internal data class VisemeIds(
    val a: String,
    val i: String,
    val u: String,
    val e: String,
    val o: String,
)

internal data class EmotionIds(
    val joy: String,
    val angry: String,
    val sorrow: String,
    val surprise: String,
    val neutral: String,
    val relaxed: String,
)

internal data class BlinkIds(
    val both: String,
    val left: String,
    val right: String,
)

internal data class GazeIds(
    val up: String,
    val down: String,
    val left: String,
    val right: String,
)

internal data class HeadIds(
    val yaw: String,
    val pitch: String,
    val roll: String,
)
