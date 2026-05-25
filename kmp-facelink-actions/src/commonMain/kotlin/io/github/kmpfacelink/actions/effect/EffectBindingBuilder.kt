package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.actions.ActionBinding
import io.github.kmpfacelink.actions.ActionTrigger
import io.github.kmpfacelink.actions.StreamEventFilter
import io.github.kmpfacelink.actions.StreamEventType
import io.github.kmpfacelink.actions.ThresholdDirection
import io.github.kmpfacelink.interact.model.EffectParameter
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.Handedness

private const val DEFAULT_GESTURE_CONFIDENCE = 0.5f
private const val DEFAULT_EXPRESSION_THRESHOLD = 0.5f
private const val DEFAULT_SCALE_MAX_DURATION_MS = 5000L

/**
 * DSL builder for declaratively constructing a list of [EffectBinding]s.
 *
 * Usage:
 * ```kotlin
 * val bindings = effectBindings {
 *     onGesture(HandGesture.VICTORY) {
 *         emit(EffectParameter.ThrowEffect(...))
 *         lifecycle(EffectLifecycleMode.ON_START)
 *     }
 *     onExpression(BlendShape.TONGUE_OUT) {
 *         emit(EffectParameter.ScreenShake(...))
 *     }
 * }
 * ```
 */
public class EffectBindingBuilder {
    private val bindings = mutableListOf<EffectBinding>()
    private var counter = 0

    /**
     * Declare an effect binding triggered by a hand gesture.
     *
     * @param gesture The hand gesture to detect.
     * @param hand Required handedness, or null for either hand.
     * @param minConfidence Minimum gesture confidence (0.0-1.0).
     * @param block Configuration scope for the effect declaration.
     */
    public fun onGesture(
        gesture: HandGesture,
        hand: Handedness? = null,
        minConfidence: Float = DEFAULT_GESTURE_CONFIDENCE,
        block: EffectDeclarationScope.() -> Unit,
    ) {
        val bindingId = "gesture.${gesture.name.lowercase()}.${counter++}"
        val trigger = ActionTrigger.GestureTrigger(gesture, hand, minConfidence)
        val scope = EffectDeclarationScope(bindingId)
        scope.block()
        val actionBinding = ActionBinding(
            actionId = bindingId,
            trigger = trigger,
            cooldownMs = scope.cooldownMs,
            debounceMs = scope.debounceMs,
            emitHeldEvents = scope.lifecycle == EffectLifecycleMode.WHILE_HELD,
        )
        bindings.add(scope.build(actionBinding))
    }

    /**
     * Declare an effect binding triggered by a facial expression (blend shape).
     *
     * @param blendShape The blend shape to monitor.
     * @param threshold The threshold value (0.0-1.0).
     * @param direction Whether to activate above or below the threshold.
     * @param block Configuration scope for the effect declaration.
     */
    public fun onExpression(
        blendShape: BlendShape,
        threshold: Float = DEFAULT_EXPRESSION_THRESHOLD,
        direction: ThresholdDirection = ThresholdDirection.ABOVE,
        block: EffectDeclarationScope.() -> Unit,
    ) {
        val bindingId = "expression.${blendShape.name.lowercase()}.${counter++}"
        val trigger = ActionTrigger.ExpressionTrigger(blendShape, threshold, direction)
        val scope = EffectDeclarationScope(bindingId)
        scope.block()
        val actionBinding = ActionBinding(
            actionId = bindingId,
            trigger = trigger,
            cooldownMs = scope.cooldownMs,
            debounceMs = scope.debounceMs,
            emitHeldEvents = scope.lifecycle == EffectLifecycleMode.WHILE_HELD,
        )
        bindings.add(scope.build(actionBinding))
    }

    /**
     * Declare an effect binding triggered by a stream interaction event.
     *
     * @param eventType The type of stream event to match.
     * @param filter Additional filter criteria.
     * @param block Configuration scope for the effect declaration.
     */
    public fun onStreamEvent(
        eventType: StreamEventType,
        filter: StreamEventFilter = StreamEventFilter.Any,
        block: EffectDeclarationScope.() -> Unit,
    ) {
        val bindingId = "stream.${eventType.name.lowercase()}.${counter++}"
        val trigger = ActionTrigger.StreamEventTrigger(eventType, filter)
        val scope = EffectDeclarationScope(bindingId)
        scope.block()
        val actionBinding = ActionBinding(
            actionId = bindingId,
            trigger = trigger,
            cooldownMs = scope.cooldownMs,
            debounceMs = scope.debounceMs,
            emitHeldEvents = scope.lifecycle == EffectLifecycleMode.WHILE_HELD,
        )
        bindings.add(scope.build(actionBinding))
    }

    internal fun build(): List<EffectBinding> = bindings.toList()
}

/**
 * Scope for configuring a single effect binding within the DSL.
 *
 * @property bindingId The unique identifier assigned to this binding.
 */
public class EffectDeclarationScope(private val bindingId: String) {
    private var effect: EffectParameter? = null
    internal var lifecycle: EffectLifecycleMode = EffectLifecycleMode.ON_START
        private set
    private var blend: EffectBlendMode = EffectBlendMode.ADDITIVE
    internal var cooldownMs: Long = 0L
        private set
    internal var debounceMs: Long = 0L
        private set
    private var priorityValue: Int = 0
    private var maxConcurrentValue: Int = EffectBinding.DEFAULT_MAX_CONCURRENT
    private var scaled: Boolean = false
    private var maxDurationMs: Long = DEFAULT_SCALE_MAX_DURATION_MS

    /** Set the effect to emit when this binding triggers. */
    public fun emit(effect: EffectParameter) {
        this.effect = effect
    }

    /** Set the lifecycle mode for emission timing. */
    public fun lifecycle(mode: EffectLifecycleMode) {
        this.lifecycle = mode
    }

    /** Set how concurrent effect instances are combined. */
    public fun blendMode(mode: EffectBlendMode) {
        this.blend = mode
    }

    /** Set the cooldown period in milliseconds after release. */
    public fun cooldown(ms: Long) {
        this.cooldownMs = ms
    }

    /** Set the debounce period in milliseconds for trigger stability. */
    public fun debounce(ms: Long) {
        this.debounceMs = ms
    }

    /** Set the priority for PRIORITY blend mode resolution (higher wins). */
    public fun priority(value: Int) {
        this.priorityValue = value
    }

    /** Set the maximum concurrent effect instances from this binding. */
    public fun maxConcurrent(count: Int) {
        this.maxConcurrentValue = count
    }

    /**
     * Enable duration-scaled intensity. The effect intensity will scale
     * proportionally to hold duration up to [maxMs].
     */
    public fun scaleWithDuration(maxMs: Long = DEFAULT_SCALE_MAX_DURATION_MS) {
        this.scaled = true
        this.maxDurationMs = maxMs
    }

    internal fun build(actionBinding: ActionBinding): EffectBinding {
        val effectParam = requireNotNull(effect) {
            "EffectDeclarationScope for '$bindingId' must call emit() with an EffectParameter"
        }
        val factory: EffectFactory = if (scaled) {
            EffectFactory.Scaled(effectParam, maxDurationMs)
        } else {
            EffectFactory.Static(effectParam)
        }
        return EffectBinding(
            bindingId = bindingId,
            actionBinding = actionBinding,
            effectFactory = factory,
            lifecycleMode = lifecycle,
            blendMode = blend,
            priority = priorityValue,
            maxConcurrent = maxConcurrentValue,
        )
    }
}

/**
 * Top-level DSL function for building a list of [EffectBinding]s.
 *
 * @param block Builder scope for declaring effect bindings.
 * @return The list of configured [EffectBinding]s.
 */
public fun effectBindings(block: EffectBindingBuilder.() -> Unit): List<EffectBinding> {
    return EffectBindingBuilder().apply(block).build()
}
