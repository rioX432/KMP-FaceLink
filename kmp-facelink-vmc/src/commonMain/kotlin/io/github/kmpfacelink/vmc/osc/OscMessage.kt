package io.github.kmpfacelink.vmc.osc

/**
 * Represents an OSC (Open Sound Control) message with an address pattern and arguments.
 *
 * OSC messages consist of an address pattern (starting with `/`), a type tag string,
 * and zero or more arguments. This class handles serialization to and deserialization
 * from the OSC 1.0 binary wire format.
 *
 * @property address The OSC address pattern (e.g., `/VMC/Ext/Blend/Val`)
 * @property arguments List of typed arguments (Int, Float, or String)
 */
public data class OscMessage(
    val address: String,
    val arguments: List<OscArgument>,
) {
    public companion object
}

/**
 * A typed OSC argument. OSC supports int32, float32, and string types.
 */
public sealed interface OscArgument {

    /** OSC int32 argument. */
    public data class IntArg(public val value: Int) : OscArgument

    /** OSC float32 argument. */
    public data class FloatArg(public val value: Float) : OscArgument

    /** OSC string argument. */
    public data class StringArg(public val value: String) : OscArgument
}
