package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.Body3DConfig
import io.github.kmpfacelink.model.LiftingStrategy

/**
 * Creates a [PoseLiftingStrategy] instance from a [Body3DConfig].
 *
 * @return The configured strategy, or null if 3D lifting is disabled.
 */
public fun Body3DConfig.createLiftingStrategy(): PoseLiftingStrategy? {
    if (!enabled) return null
    return when (strategy) {
        LiftingStrategy.STATISTICAL -> StatisticalPoseLifter()
        LiftingStrategy.PERSPECTIVE -> DepthFromPerspective(shoulderReferenceWidth)
    }
}
