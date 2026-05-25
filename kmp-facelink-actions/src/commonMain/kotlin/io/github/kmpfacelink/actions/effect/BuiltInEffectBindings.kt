package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.actions.StreamEventFilter
import io.github.kmpfacelink.actions.StreamEventType
import io.github.kmpfacelink.interact.model.AvatarAnchor
import io.github.kmpfacelink.interact.model.EffectParameter
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.HandGesture

private const val CONFETTI_DURATION_MS = 2000L
private const val CONFETTI_INTENSITY = 0.9f
private const val CONFETTI_COUNT = 10

private const val FIREWORKS_DURATION_MS = 3000L
private const val FIREWORKS_INTENSITY = 1.0f
private const val FIREWORKS_COUNT = 5

private const val STAR_BURST_DURATION_MS = 2500L
private const val STAR_BURST_INTENSITY = 1.0f
private const val STAR_BURST_COUNT = 8

private const val SPARKLE_DURATION_MS = 1500L
private const val SPARKLE_INTENSITY = 0.7f
private const val SPARKLE_COUNT = 3

private const val SHAKE_DURATION_MS = 500L
private const val SHAKE_INTENSITY = 0.6f
private const val SHAKE_AMPLITUDE = 0.15f

private const val POP_DURATION_MS = 800L
private const val POP_INTENSITY = 0.8f
private const val POP_COUNT = 1

private const val GIFT_THROW_DURATION_MS = 2000L
private const val GIFT_THROW_INTENSITY = 1.0f
private const val GIFT_THROW_COUNT = 5
private const val GIFT_MIN_AMOUNT = 5.0

private const val RAID_SHAKE_DURATION_MS = 1000L
private const val RAID_SHAKE_INTENSITY = 0.8f
private const val RAID_SHAKE_AMPLITUDE = 0.25f

private const val HEARTS_DURATION_MS = 2000L
private const val HEARTS_INTENSITY = 0.7f
private const val HEARTS_COUNT = 6

private const val EXPRESSION_THRESHOLD = 0.6f
private const val MOUTH_OPEN_THRESHOLD = 0.7f
private const val COOLDOWN_MS = 1000L

/**
 * Pre-built effect binding packs for common VTuber interaction patterns.
 *
 * Each pack returns a list of [EffectBinding]s that can be registered
 * directly with [EffectBindingEngine] via [EffectBindingEngine.registerAll].
 */
public object BuiltInEffectBindings {

    /**
     * Celebration gesture pack: hand gestures trigger celebratory effects.
     *
     * - Peace sign -> confetti throw from head
     * - Thumbs up -> fireworks from body
     * - Victory -> star burst from screen
     */
    public fun celebrationPack(): List<EffectBinding> = effectBindings {
        onGesture(HandGesture.VICTORY) {
            emit(confettiEffect())
            cooldown(COOLDOWN_MS)
        }
        onGesture(HandGesture.THUMB_UP) {
            emit(fireworksEffect())
            cooldown(COOLDOWN_MS)
        }
        onGesture(HandGesture.OPEN_PALM) {
            emit(starBurstEffect())
            cooldown(COOLDOWN_MS)
        }
    }

    /**
     * Expression reaction pack: facial expressions trigger visual effects.
     *
     * - Wink left -> sparkle from left hand
     * - Surprised (mouth open wide) -> screen shake
     * - Tongue out -> pop effect from head
     */
    public fun expressionPack(): List<EffectBinding> = effectBindings {
        onExpression(BlendShape.EYE_BLINK_LEFT, threshold = EXPRESSION_THRESHOLD) {
            emit(sparkleEffect())
            cooldown(COOLDOWN_MS)
        }
        onExpression(BlendShape.JAW_OPEN, threshold = MOUTH_OPEN_THRESHOLD) {
            emit(surpriseShakeEffect())
            cooldown(COOLDOWN_MS)
        }
        onExpression(BlendShape.TONGUE_OUT, threshold = EXPRESSION_THRESHOLD) {
            emit(popEffect())
            cooldown(COOLDOWN_MS)
        }
    }

    /**
     * Stream interaction pack: stream events trigger audience-visible effects.
     *
     * - Gift (min $5) -> throw effect from screen
     * - Raid -> screen shake
     * - Follow -> hearts from body
     */
    public fun streamPack(): List<EffectBinding> = effectBindings {
        onStreamEvent(StreamEventType.GIFT, StreamEventFilter.MinAmount(GIFT_MIN_AMOUNT)) {
            emit(giftThrowEffect())
        }
        onStreamEvent(StreamEventType.RAID) {
            emit(raidShakeEffect())
        }
        onStreamEvent(StreamEventType.FOLLOW) {
            emit(heartsEffect())
        }
    }
}

private fun confettiEffect(): EffectParameter = EffectParameter.ThrowEffect(
    durationMs = CONFETTI_DURATION_MS,
    intensity = CONFETTI_INTENSITY,
    anchor = AvatarAnchor.HEAD,
    objectName = "confetti",
    count = CONFETTI_COUNT,
)

private fun fireworksEffect(): EffectParameter = EffectParameter.ThrowEffect(
    durationMs = FIREWORKS_DURATION_MS,
    intensity = FIREWORKS_INTENSITY,
    anchor = AvatarAnchor.BODY,
    objectName = "fireworks",
    count = FIREWORKS_COUNT,
)

private fun starBurstEffect(): EffectParameter = EffectParameter.ThrowEffect(
    durationMs = STAR_BURST_DURATION_MS,
    intensity = STAR_BURST_INTENSITY,
    anchor = AvatarAnchor.SCREEN,
    objectName = "star",
    count = STAR_BURST_COUNT,
)

private fun sparkleEffect(): EffectParameter = EffectParameter.ThrowEffect(
    durationMs = SPARKLE_DURATION_MS,
    intensity = SPARKLE_INTENSITY,
    anchor = AvatarAnchor.LEFT_HAND,
    objectName = "sparkle",
    count = SPARKLE_COUNT,
)

private fun surpriseShakeEffect(): EffectParameter = EffectParameter.ScreenShake(
    durationMs = SHAKE_DURATION_MS,
    intensity = SHAKE_INTENSITY,
    amplitude = SHAKE_AMPLITUDE,
)

private fun popEffect(): EffectParameter = EffectParameter.ThrowEffect(
    durationMs = POP_DURATION_MS,
    intensity = POP_INTENSITY,
    anchor = AvatarAnchor.HEAD,
    objectName = "pop",
    count = POP_COUNT,
)

private fun giftThrowEffect(): EffectParameter = EffectParameter.ThrowEffect(
    durationMs = GIFT_THROW_DURATION_MS,
    intensity = GIFT_THROW_INTENSITY,
    anchor = AvatarAnchor.SCREEN,
    objectName = "gift",
    count = GIFT_THROW_COUNT,
)

private fun raidShakeEffect(): EffectParameter = EffectParameter.ScreenShake(
    durationMs = RAID_SHAKE_DURATION_MS,
    intensity = RAID_SHAKE_INTENSITY,
    amplitude = RAID_SHAKE_AMPLITUDE,
)

private fun heartsEffect(): EffectParameter = EffectParameter.ThrowEffect(
    durationMs = HEARTS_DURATION_MS,
    intensity = HEARTS_INTENSITY,
    anchor = AvatarAnchor.BODY,
    objectName = "heart",
    count = HEARTS_COUNT,
)
