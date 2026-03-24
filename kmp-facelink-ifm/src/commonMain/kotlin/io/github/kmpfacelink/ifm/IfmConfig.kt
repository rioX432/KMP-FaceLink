package io.github.kmpfacelink.ifm

/**
 * Configuration for iFacialMocap protocol communication.
 *
 * @property host Target host address for the iFacialMocap receiver
 * @property port Target UDP port for sending tracking data.
 *           Default is [DEFAULT_PORT] (49983).
 * @property dataVersion Protocol data version ([DataVersion.V1] or [DataVersion.V2]).
 *           V1 uses `-` as the name-value separator; V2 uses `&`.
 */
public data class IfmConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val dataVersion: DataVersion = DataVersion.V1,
) {
    public companion object {
        /** Default iFacialMocap UDP port for handshake and data. */
        public const val DEFAULT_PORT: Int = 49983

        /** Default target host (localhost). */
        public const val DEFAULT_HOST: String = "127.0.0.1"
    }
}

/**
 * iFacialMocap protocol data version.
 *
 * - [V1]: Uses `-` (dash) between blendshape name and value. Original format.
 * - [V2]: Uses `&` (ampersand) between blendshape name and value.
 *         Avoids collisions with negative values (used by Facemotion3d).
 */
public enum class DataVersion {
    /** Original format: `blendShapeName-value` separated by `|`. */
    V1,

    /** Extended format: `blendShapeName&value` separated by `|`. Appends `|sendDataVersion=v2`. */
    V2,
}
