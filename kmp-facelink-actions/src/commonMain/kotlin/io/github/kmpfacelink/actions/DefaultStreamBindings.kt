package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.BlendShape

private const val DEFAULT_MIN_GIFT_AMOUNT = 1.0

/**
 * Pre-configured [ActionBinding] factories for common stream interaction use cases.
 *
 * These produce bindings with sensible defaults that can be registered
 * directly with [ActionSystem]. The consumer app maps the emitted
 * [ActionEvent][ActionEvent.Started] action IDs to its rendering logic
 * (e.g., showing throw animations, applying expression overrides).
 */
public object DefaultStreamBindings {

    /**
     * Triggers when a gift/donation above [minAmount] is received.
     *
     * The consumer app should map the "stream.gift.throw" action ID
     * to a [ThrowEffect][io.github.kmpfacelink.interact.model.EffectParameter.ThrowEffect].
     *
     * @param minAmount Minimum gift amount in USD to trigger (default: 1.0).
     * @return An [ActionBinding] with action ID "stream.gift.throw".
     */
    public fun giftThrow(
        minAmount: Double = DEFAULT_MIN_GIFT_AMOUNT,
    ): ActionBinding = ActionBinding(
        actionId = "stream.gift.throw",
        trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.GIFT,
            filter = StreamEventFilter.MinAmount(minAmount),
        ),
    )

    /**
     * Triggers when a raid occurs.
     *
     * The consumer app should map the "stream.raid.shake" action ID
     * to a [ScreenShake][io.github.kmpfacelink.interact.model.EffectParameter.ScreenShake].
     *
     * @return An [ActionBinding] with action ID "stream.raid.shake".
     */
    public fun raidScreenShake(): ActionBinding = ActionBinding(
        actionId = "stream.raid.shake",
        trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.RAID,
            filter = StreamEventFilter.Any,
        ),
    )

    /**
     * Triggers when a specific channel point reward is redeemed.
     *
     * The consumer app should map the "stream.channelpoint.expression" action ID
     * to an [ExpressionOverride][io.github.kmpfacelink.interact.model.EffectParameter.ExpressionOverride]
     * using the provided [blendShapes].
     *
     * @param rewardTitle Exact title of the channel point reward.
     * @param blendShapes Map of blend shapes to target values for the expression override.
     *   Not stored in the binding itself; the consumer must retain this mapping.
     * @return An [ActionBinding] with action ID "stream.channelpoint.expression".
     */
    @Suppress("UnusedParameter")
    public fun channelPointExpression(
        rewardTitle: String,
        blendShapes: Map<BlendShape, Float>,
    ): ActionBinding = ActionBinding(
        actionId = "stream.channelpoint.expression",
        trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.CHANNEL_POINT_REDEMPTION,
            filter = StreamEventFilter.RewardTitle(rewardTitle),
        ),
    )
}
