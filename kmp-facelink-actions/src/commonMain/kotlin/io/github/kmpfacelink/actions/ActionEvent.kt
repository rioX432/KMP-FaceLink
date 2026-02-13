package io.github.kmpfacelink.actions

/**
 * Events emitted by [ActionSystem] when triggers activate or deactivate.
 */
public sealed class ActionEvent {
    /** The action identifier from the binding. */
    public abstract val actionId: String

    /** Timestamp in milliseconds when this event occurred. */
    public abstract val timestampMs: Long

    /**
     * Emitted when a trigger's hold time has elapsed and the action becomes active.
     */
    public data class Started(
        override val actionId: String,
        override val timestampMs: Long,
    ) : ActionEvent()

    /**
     * Emitted each frame while the action remains active (opt-in via [ActionBinding.emitHeldEvents]).
     *
     * @property durationMs Time in ms since the action started.
     */
    public data class Held(
        override val actionId: String,
        override val timestampMs: Long,
        val durationMs: Long,
    ) : ActionEvent()

    /**
     * Emitted when the trigger condition is no longer met and the action deactivates.
     *
     * @property totalDurationMs Total time in ms the action was active.
     */
    public data class Released(
        override val actionId: String,
        override val timestampMs: Long,
        val totalDurationMs: Long,
    ) : ActionEvent()
}
