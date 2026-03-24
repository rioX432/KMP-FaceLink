package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShape

/**
 * Mapping between KMP-FaceLink [BlendShape] names and VMC Protocol blend shape names.
 *
 * VMC Protocol uses ARKit-style camelCase naming for blend shapes in `/VMC/Ext/Blend/Val`
 * messages. This object provides the canonical mapping from our enum to those names.
 */
internal object VmcBoneMapping {

    /** VMC bone name for head tracking. */
    const val HEAD_BONE = "Head"

    /**
     * Maps all 52 [BlendShape] entries to their VMC-compatible ARKit camelCase names.
     * Uses the existing [BlendShape.arKitName] property.
     */
    fun blendShapeToVmcName(shape: BlendShape): String = shape.arKitName
}
