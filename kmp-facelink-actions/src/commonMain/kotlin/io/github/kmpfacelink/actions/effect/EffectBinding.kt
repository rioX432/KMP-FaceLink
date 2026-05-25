package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.actions.ActionBinding

/**
 * Bridges an [ActionBinding] (trigger side) to an [EffectFactory] (output side),
 * defining how action events are translated into visual effects.
 *
 * @property bindingId Unique identifier for this effect binding.
 * @property actionBinding The action binding that triggers this effect.
 * @property effectFactory Strategy for producing effect parameters from action events.
 * @property lifecycleMode When to emit effects relative to the action lifecycle.
 * @property blendMode How concurrent effect instances are combined.
 * @property priority Priority for [EffectBlendMode.PRIORITY] resolution (higher wins).
 * @property maxConcurrent Maximum concurrent effect instances from this binding.
 */
public data class EffectBinding(
    val bindingId: String,
    val actionBinding: ActionBinding,
    val effectFactory: EffectFactory,
    val lifecycleMode: EffectLifecycleMode = EffectLifecycleMode.ON_START,
    val blendMode: EffectBlendMode = EffectBlendMode.ADDITIVE,
    val priority: Int = 0,
    val maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
) {
    internal companion object {
        const val DEFAULT_MAX_CONCURRENT = 3
    }
}
