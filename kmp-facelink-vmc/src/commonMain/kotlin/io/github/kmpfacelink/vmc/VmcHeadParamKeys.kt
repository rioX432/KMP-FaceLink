package io.github.kmpfacelink.vmc

/**
 * Identifies which keys in an expression parameter map represent head rotation.
 *
 * When sending VRM expression parameters via VMC, head rotation values
 * need to be extracted from the expression map and sent as bone position
 * messages instead of blend shape values.
 *
 * @property yaw Key for head yaw (horizontal rotation) in degrees
 * @property pitch Key for head pitch (vertical rotation) in degrees
 * @property roll Key for head roll (tilt) in degrees
 */
public data class VmcHeadParamKeys(
    val yaw: String,
    val pitch: String,
    val roll: String,
) {
    public companion object {
        /** Head parameter keys matching VRM 1.0 expression IDs. */
        public val VRM_1_0: VmcHeadParamKeys = VmcHeadParamKeys(
            yaw = "headYaw",
            pitch = "headPitch",
            roll = "headRoll",
        )

        /** Head parameter keys matching VRM 0.x expression IDs. */
        public val VRM_0_X: VmcHeadParamKeys = VmcHeadParamKeys(
            yaw = "HeadYaw",
            pitch = "HeadPitch",
            roll = "HeadRoll",
        )
    }
}
