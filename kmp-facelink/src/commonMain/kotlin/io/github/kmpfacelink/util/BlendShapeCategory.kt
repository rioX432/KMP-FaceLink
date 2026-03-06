package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape

/** Facial expression category used to route blend shapes to per-category smoothing filters. */
internal enum class BlendShapeCategory { MOUTH, EYE, BROW, CHEEK, JAW, NOSE, OTHER }

/** Returns the [BlendShapeCategory] for this blend shape based on its name prefix. */
internal fun BlendShape.category(): BlendShapeCategory = when {
    name.startsWith("MOUTH_") || this == BlendShape.TONGUE_OUT -> BlendShapeCategory.MOUTH
    name.startsWith("EYE_") -> BlendShapeCategory.EYE
    name.startsWith("BROW_") -> BlendShapeCategory.BROW
    name.startsWith("CHEEK_") -> BlendShapeCategory.CHEEK
    name.startsWith("JAW_") -> BlendShapeCategory.JAW
    name.startsWith("NOSE_") -> BlendShapeCategory.NOSE
    else -> BlendShapeCategory.OTHER
}
