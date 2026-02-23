package io.github.kmpfacelink.rive

/**
 * Represents a Rive State Machine input value.
 *
 * Rive state machines accept three input types: numbers, booleans, and triggers.
 */
public sealed class RiveInput {

    /**
     * A numeric input (maps to Rive's Number input type).
     *
     * @property value the float value to set
     */
    public data class Number(val value: Float) : RiveInput()

    /**
     * A boolean input (maps to Rive's Boolean input type).
     *
     * @property value the boolean value to set
     */
    public data class BooleanInput(val value: Boolean) : RiveInput()

    /**
     * A trigger input (maps to Rive's Trigger input type).
     *
     * Triggers are fire-and-forget — they have no persistent value.
     */
    public data object Trigger : RiveInput()
}
