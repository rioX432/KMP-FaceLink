package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VmcBoneMappingTest {

    @Test
    fun headBoneMatchesVrm10Spec() {
        assertEquals("Head", VmcBoneMapping.HEAD)
        assertEquals(VmcBoneMapping.HEAD, VmcBoneMapping.HEAD_BONE)
    }

    @Test
    fun requiredBonesContainsMinimalSet() {
        val required = VmcBoneMapping.REQUIRED_BONES
        // VRM 1.0 requires 16 bones minimum
        assertEquals(REQUIRED_BONE_COUNT, required.size)
        assertTrue("Hips" in required)
        assertTrue("Spine" in required)
        assertTrue("Chest" in required)
        assertTrue("Head" in required)
        assertTrue("LeftUpperArm" in required)
        assertTrue("RightHand" in required)
        assertTrue("LeftFoot" in required)
    }

    @Test
    fun allBonesContainsComplete55Set() {
        val all = VmcBoneMapping.ALL_BONES
        assertEquals(ALL_BONE_COUNT, all.size)
    }

    @Test
    fun allBonesContainsAllRequiredBones() {
        assertTrue(VmcBoneMapping.ALL_BONES.containsAll(VmcBoneMapping.REQUIRED_BONES))
    }

    @Test
    fun allBonesContainsOptionalBones() {
        val all = VmcBoneMapping.ALL_BONES
        assertTrue("LeftEye" in all)
        assertTrue("RightEye" in all)
        assertTrue("Jaw" in all)
        assertTrue("Neck" in all)
        assertTrue("UpperChest" in all)
        assertTrue("LeftToes" in all)
        assertTrue("RightToes" in all)
        assertTrue("LeftShoulder" in all)
        assertTrue("RightShoulder" in all)
    }

    @Test
    fun allBonesContainsFingerBones() {
        val all = VmcBoneMapping.ALL_BONES
        assertTrue("LeftThumbMetacarpal" in all)
        assertTrue("LeftIndexProximal" in all)
        assertTrue("LeftMiddleIntermediate" in all)
        assertTrue("LeftRingDistal" in all)
        assertTrue("LeftLittleProximal" in all)
        assertTrue("RightThumbDistal" in all)
        assertTrue("RightIndexIntermediate" in all)
        assertTrue("RightMiddleProximal" in all)
        assertTrue("RightRingIntermediate" in all)
        assertTrue("RightLittleDistal" in all)
    }

    @Test
    fun blendShapeToVmcNameUsesArKitNames() {
        assertEquals("eyeBlinkLeft", VmcBoneMapping.blendShapeToVmcName(BlendShape.EYE_BLINK_LEFT))
        assertEquals("jawOpen", VmcBoneMapping.blendShapeToVmcName(BlendShape.JAW_OPEN))
        assertEquals("tongueOut", VmcBoneMapping.blendShapeToVmcName(BlendShape.TONGUE_OUT))
    }

    @Test
    fun boneNamesUsePascalCase() {
        // VRM 1.0 bone names are PascalCase (matching Unity convention)
        for (bone in VmcBoneMapping.ALL_BONES) {
            assertTrue(
                bone[0].isUpperCase(),
                "Bone name '$bone' should start with an uppercase letter",
            )
        }
    }
}

private const val REQUIRED_BONE_COUNT = 16
private const val ALL_BONE_COUNT = 55
