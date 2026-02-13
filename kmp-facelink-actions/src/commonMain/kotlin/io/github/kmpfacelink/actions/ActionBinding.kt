package io.github.kmpfacelink.actions

/**
 * Binds a trigger to an action with anti-misfire configuration.
 *
 * @property actionId Unique identifier for this action.
 * @property trigger The condition that activates this action.
 * @property holdTimeMs Time in ms the trigger must be held before activation (default: 0 = immediate).
 * @property cooldownMs Time in ms after release before the trigger can re-activate (default: 0).
 * @property debounceMs Grace period in ms — brief condition losses during ACTIVE phase are ignored (default: 0).
 * @property emitHeldEvents Whether to emit [ActionEvent.Held] each frame while active (default: false).
 */
public data class ActionBinding(
    val actionId: String,
    val trigger: ActionTrigger,
    val holdTimeMs: Long = 0L,
    val cooldownMs: Long = 0L,
    val debounceMs: Long = 0L,
    val emitHeldEvents: Boolean = false,
)
