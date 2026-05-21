package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.Handedness

/**
 * Defines a condition that activates an action.
 */
public sealed class ActionTrigger {

    /**
     * Triggers when a specific hand gesture is detected.
     *
     * @property gesture The gesture to detect.
     * @property hand Required handedness, or null for either hand.
     * @property minConfidence Minimum gesture confidence (0.0–1.0).
     */
    public data class GestureTrigger(
        val gesture: HandGesture,
        val hand: Handedness? = null,
        val minConfidence: Float = 0.5f,
    ) : ActionTrigger()

    /**
     * Triggers when a blend shape crosses a threshold.
     *
     * @property blendShape The blend shape to monitor.
     * @property threshold The threshold value (0.0–1.0).
     * @property direction Whether to activate above or below the threshold.
     */
    public data class ExpressionTrigger(
        val blendShape: BlendShape,
        val threshold: Float = 0.5f,
        val direction: ThresholdDirection = ThresholdDirection.ABOVE,
    ) : ActionTrigger()

    /**
     * Triggers when ALL sub-triggers are simultaneously true.
     * Sub-triggers must be [GestureTrigger] or [ExpressionTrigger] (no nesting).
     *
     * @property triggers Flat list of sub-triggers (must not contain [CombinedTrigger]).
     */
    public data class CombinedTrigger(
        val triggers: List<ActionTrigger>,
    ) : ActionTrigger() {
        init {
            require(triggers.size >= 2) { "CombinedTrigger requires at least 2 sub-triggers" }
            require(triggers.none { it is CombinedTrigger }) {
                "CombinedTrigger cannot contain nested CombinedTriggers"
            }
        }
    }

    /**
     * Triggers when a matching stream interaction event is received.
     *
     * Unlike [GestureTrigger] and [ExpressionTrigger] which are evaluated per-frame,
     * stream event triggers are evaluated once when a [StreamEvent][io.github.kmpfacelink.interact.model.StreamEvent]
     * is passed to [ActionSystem.processStreamEvent].
     *
     * @property eventType The type of stream event to match.
     * @property filter Additional filter criteria (default: [StreamEventFilter.Any]).
     */
    public data class StreamEventTrigger(
        val eventType: StreamEventType,
        val filter: StreamEventFilter = StreamEventFilter.Any,
    ) : ActionTrigger()
}
