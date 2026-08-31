package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData

/**
 * The value to drive an avatar parameter with, treating a shape the estimator did not produce as
 * the rest pose.
 *
 * An avatar has to be drawn every frame, so a parameter with no incoming value has to fall back to
 * something, and the rest pose is the only choice that does not invent movement. That makes zero
 * the right substitute *here*, at the point where values become motion — and the wrong one earlier,
 * where a caller can still tell "measured as zero" from "never produced". Keeping the substitution
 * in the avatar layer is what preserves that distinction upstream.
 */
internal fun BlendShapeData.valueOrRestPose(shape: BlendShape): Float = this[shape] ?: 0f
