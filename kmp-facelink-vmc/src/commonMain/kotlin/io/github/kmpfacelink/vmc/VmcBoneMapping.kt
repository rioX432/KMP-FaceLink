package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShape

/**
 * Mapping between KMP-FaceLink [BlendShape] names and VMC Protocol blend shape names,
 * plus VRM 1.0 humanoid bone name constants.
 *
 * VMC Protocol uses ARKit-style camelCase naming for blend shapes in `/VMC/Ext/Blend/Val`
 * messages. Bone names follow the VRM 1.0 humanoid specification for `/VMC/Ext/Bone/Pos`
 * messages.
 *
 * @see <a href="https://protocol.vmc.info/english.html">VMC Protocol Specification</a>
 * @see <a href="https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/humanoid.md">VRM 1.0 Humanoid Spec</a>
 */
public object VmcBoneMapping {

    // -- Torso bones --

    /** VRM 1.0 bone: root of the humanoid hierarchy. */
    public const val HIPS: String = "Hips"

    /** VRM 1.0 bone: first spinal vertebra above hips. */
    public const val SPINE: String = "Spine"

    /** VRM 1.0 bone: upper thorax (optional). */
    public const val CHEST: String = "Chest"

    /** VRM 1.0 bone: top of thorax (optional). */
    public const val UPPER_CHEST: String = "UpperChest"

    /** VRM 1.0 bone: cervical vertebrae (optional). */
    public const val NECK: String = "Neck"

    // -- Head --

    /** VRM 1.0 bone: head. Primary bone for face tracking. */
    public const val HEAD: String = "Head"

    /** VRM 1.0 bone: left eye (optional). */
    public const val LEFT_EYE: String = "LeftEye"

    /** VRM 1.0 bone: right eye (optional). */
    public const val RIGHT_EYE: String = "RightEye"

    /** VRM 1.0 bone: jaw (optional). */
    public const val JAW: String = "Jaw"

    // -- Left leg --

    /** VRM 1.0 bone: left thigh. */
    public const val LEFT_UPPER_LEG: String = "LeftUpperLeg"

    /** VRM 1.0 bone: left shin. */
    public const val LEFT_LOWER_LEG: String = "LeftLowerLeg"

    /** VRM 1.0 bone: left foot. */
    public const val LEFT_FOOT: String = "LeftFoot"

    /** VRM 1.0 bone: left toes (optional). */
    public const val LEFT_TOES: String = "LeftToes"

    // -- Right leg --

    /** VRM 1.0 bone: right thigh. */
    public const val RIGHT_UPPER_LEG: String = "RightUpperLeg"

    /** VRM 1.0 bone: right shin. */
    public const val RIGHT_LOWER_LEG: String = "RightLowerLeg"

    /** VRM 1.0 bone: right foot. */
    public const val RIGHT_FOOT: String = "RightFoot"

    /** VRM 1.0 bone: right toes (optional). */
    public const val RIGHT_TOES: String = "RightToes"

    // -- Left arm --

    /** VRM 1.0 bone: left shoulder (optional). */
    public const val LEFT_SHOULDER: String = "LeftShoulder"

    /** VRM 1.0 bone: left upper arm. */
    public const val LEFT_UPPER_ARM: String = "LeftUpperArm"

    /** VRM 1.0 bone: left forearm. */
    public const val LEFT_LOWER_ARM: String = "LeftLowerArm"

    /** VRM 1.0 bone: left hand. */
    public const val LEFT_HAND: String = "LeftHand"

    // -- Right arm --

    /** VRM 1.0 bone: right shoulder (optional). */
    public const val RIGHT_SHOULDER: String = "RightShoulder"

    /** VRM 1.0 bone: right upper arm. */
    public const val RIGHT_UPPER_ARM: String = "RightUpperArm"

    /** VRM 1.0 bone: right forearm. */
    public const val RIGHT_LOWER_ARM: String = "RightLowerArm"

    /** VRM 1.0 bone: right hand. */
    public const val RIGHT_HAND: String = "RightHand"

    // -- Left fingers --

    /** VRM 1.0 bone: left thumb metacarpal (optional). */
    public const val LEFT_THUMB_METACARPAL: String = "LeftThumbMetacarpal"

    /** VRM 1.0 bone: left thumb proximal (optional). */
    public const val LEFT_THUMB_PROXIMAL: String = "LeftThumbProximal"

    /** VRM 1.0 bone: left thumb distal (optional). */
    public const val LEFT_THUMB_DISTAL: String = "LeftThumbDistal"

    /** VRM 1.0 bone: left index proximal (optional). */
    public const val LEFT_INDEX_PROXIMAL: String = "LeftIndexProximal"

    /** VRM 1.0 bone: left index intermediate (optional). */
    public const val LEFT_INDEX_INTERMEDIATE: String = "LeftIndexIntermediate"

    /** VRM 1.0 bone: left index distal (optional). */
    public const val LEFT_INDEX_DISTAL: String = "LeftIndexDistal"

    /** VRM 1.0 bone: left middle proximal (optional). */
    public const val LEFT_MIDDLE_PROXIMAL: String = "LeftMiddleProximal"

    /** VRM 1.0 bone: left middle intermediate (optional). */
    public const val LEFT_MIDDLE_INTERMEDIATE: String = "LeftMiddleIntermediate"

    /** VRM 1.0 bone: left middle distal (optional). */
    public const val LEFT_MIDDLE_DISTAL: String = "LeftMiddleDistal"

    /** VRM 1.0 bone: left ring proximal (optional). */
    public const val LEFT_RING_PROXIMAL: String = "LeftRingProximal"

    /** VRM 1.0 bone: left ring intermediate (optional). */
    public const val LEFT_RING_INTERMEDIATE: String = "LeftRingIntermediate"

    /** VRM 1.0 bone: left ring distal (optional). */
    public const val LEFT_RING_DISTAL: String = "LeftRingDistal"

    /** VRM 1.0 bone: left little proximal (optional). */
    public const val LEFT_LITTLE_PROXIMAL: String = "LeftLittleProximal"

    /** VRM 1.0 bone: left little intermediate (optional). */
    public const val LEFT_LITTLE_INTERMEDIATE: String = "LeftLittleIntermediate"

    /** VRM 1.0 bone: left little distal (optional). */
    public const val LEFT_LITTLE_DISTAL: String = "LeftLittleDistal"

    // -- Right fingers --

    /** VRM 1.0 bone: right thumb metacarpal (optional). */
    public const val RIGHT_THUMB_METACARPAL: String = "RightThumbMetacarpal"

    /** VRM 1.0 bone: right thumb proximal (optional). */
    public const val RIGHT_THUMB_PROXIMAL: String = "RightThumbProximal"

    /** VRM 1.0 bone: right thumb distal (optional). */
    public const val RIGHT_THUMB_DISTAL: String = "RightThumbDistal"

    /** VRM 1.0 bone: right index proximal (optional). */
    public const val RIGHT_INDEX_PROXIMAL: String = "RightIndexProximal"

    /** VRM 1.0 bone: right index intermediate (optional). */
    public const val RIGHT_INDEX_INTERMEDIATE: String = "RightIndexIntermediate"

    /** VRM 1.0 bone: right index distal (optional). */
    public const val RIGHT_INDEX_DISTAL: String = "RightIndexDistal"

    /** VRM 1.0 bone: right middle proximal (optional). */
    public const val RIGHT_MIDDLE_PROXIMAL: String = "RightMiddleProximal"

    /** VRM 1.0 bone: right middle intermediate (optional). */
    public const val RIGHT_MIDDLE_INTERMEDIATE: String = "RightMiddleIntermediate"

    /** VRM 1.0 bone: right middle distal (optional). */
    public const val RIGHT_MIDDLE_DISTAL: String = "RightMiddleDistal"

    /** VRM 1.0 bone: right ring proximal (optional). */
    public const val RIGHT_RING_PROXIMAL: String = "RightRingProximal"

    /** VRM 1.0 bone: right ring intermediate (optional). */
    public const val RIGHT_RING_INTERMEDIATE: String = "RightRingIntermediate"

    /** VRM 1.0 bone: right ring distal (optional). */
    public const val RIGHT_RING_DISTAL: String = "RightRingDistal"

    /** VRM 1.0 bone: right little proximal (optional). */
    public const val RIGHT_LITTLE_PROXIMAL: String = "RightLittleProximal"

    /** VRM 1.0 bone: right little intermediate (optional). */
    public const val RIGHT_LITTLE_INTERMEDIATE: String = "RightLittleIntermediate"

    /** VRM 1.0 bone: right little distal (optional). */
    public const val RIGHT_LITTLE_DISTAL: String = "RightLittleDistal"

    /**
     * Complete set of all VRM 1.0 required humanoid bone names.
     *
     * Required bones that every VRM 1.0 avatar must have.
     * Optional bones (eyes, jaw, toes, shoulders, fingers, upper chest, neck)
     * are excluded from this set.
     */
    public val REQUIRED_BONES: Set<String> = setOf(
        HIPS, SPINE, CHEST, HEAD,
        LEFT_UPPER_LEG, LEFT_LOWER_LEG, LEFT_FOOT,
        RIGHT_UPPER_LEG, RIGHT_LOWER_LEG, RIGHT_FOOT,
        LEFT_UPPER_ARM, LEFT_LOWER_ARM, LEFT_HAND,
        RIGHT_UPPER_ARM, RIGHT_LOWER_ARM, RIGHT_HAND,
    )

    /**
     * Complete set of all VRM 1.0 humanoid bone names (required + optional).
     *
     * Contains all 55 bone names from the VRM 1.0 specification. Useful for
     * validating bone names in VMC messages against the specification.
     */
    public val ALL_BONES: Set<String> = setOf(
        // Torso
        HIPS, SPINE, CHEST, UPPER_CHEST, NECK,
        // Head
        HEAD, LEFT_EYE, RIGHT_EYE, JAW,
        // Left leg
        LEFT_UPPER_LEG, LEFT_LOWER_LEG, LEFT_FOOT, LEFT_TOES,
        // Right leg
        RIGHT_UPPER_LEG, RIGHT_LOWER_LEG, RIGHT_FOOT, RIGHT_TOES,
        // Left arm
        LEFT_SHOULDER, LEFT_UPPER_ARM, LEFT_LOWER_ARM, LEFT_HAND,
        // Right arm
        RIGHT_SHOULDER, RIGHT_UPPER_ARM, RIGHT_LOWER_ARM, RIGHT_HAND,
        // Left fingers
        LEFT_THUMB_METACARPAL, LEFT_THUMB_PROXIMAL, LEFT_THUMB_DISTAL,
        LEFT_INDEX_PROXIMAL, LEFT_INDEX_INTERMEDIATE, LEFT_INDEX_DISTAL,
        LEFT_MIDDLE_PROXIMAL, LEFT_MIDDLE_INTERMEDIATE, LEFT_MIDDLE_DISTAL,
        LEFT_RING_PROXIMAL, LEFT_RING_INTERMEDIATE, LEFT_RING_DISTAL,
        LEFT_LITTLE_PROXIMAL, LEFT_LITTLE_INTERMEDIATE, LEFT_LITTLE_DISTAL,
        // Right fingers
        RIGHT_THUMB_METACARPAL, RIGHT_THUMB_PROXIMAL, RIGHT_THUMB_DISTAL,
        RIGHT_INDEX_PROXIMAL, RIGHT_INDEX_INTERMEDIATE, RIGHT_INDEX_DISTAL,
        RIGHT_MIDDLE_PROXIMAL, RIGHT_MIDDLE_INTERMEDIATE, RIGHT_MIDDLE_DISTAL,
        RIGHT_RING_PROXIMAL, RIGHT_RING_INTERMEDIATE, RIGHT_RING_DISTAL,
        RIGHT_LITTLE_PROXIMAL, RIGHT_LITTLE_INTERMEDIATE, RIGHT_LITTLE_DISTAL,
    )

    /**
     * Backward-compatible alias for [HEAD].
     */
    internal const val HEAD_BONE: String = HEAD

    /**
     * Maps all 52 [BlendShape] entries to their VMC-compatible ARKit camelCase names.
     * Uses the existing [BlendShape.arKitName] property.
     */
    public fun blendShapeToVmcName(shape: BlendShape): String = shape.arKitName
}
