package io.github.kmpfacelink.vmc

/**
 * VMC Protocol OSC address constants.
 *
 * These addresses follow the VMC Protocol specification for communication
 * between motion capture performers and avatar renderers (marionettes).
 *
 * @see <a href="https://protocol.vmc.info/english.html">VMC Protocol Specification</a>
 */
public object VmcAddress {

    /** BlendShape value: `(string)name (float)value` */
    public const val BLEND_VAL: String = "/VMC/Ext/Blend/Val"

    /** BlendShape apply signal: sent after all Blend/Val messages in a frame. */
    public const val BLEND_APPLY: String = "/VMC/Ext/Blend/Apply"

    /** Bone position and rotation: `(string)name (float)px,py,pz (float)qx,qy,qz,qw` */
    public const val BONE_POS: String = "/VMC/Ext/Bone/Pos"

    /** Status/OK: `(int)loaded [(int)calibrationState (int)calibrationMode [(int)trackingStatus]]` */
    public const val OK: String = "/VMC/Ext/OK"

    /** Timestamp: `(float)time` — relative time of the sender for synchronization. */
    public const val TIME: String = "/VMC/Ext/T"
}
