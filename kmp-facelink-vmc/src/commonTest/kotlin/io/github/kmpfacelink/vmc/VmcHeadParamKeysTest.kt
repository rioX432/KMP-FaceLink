package io.github.kmpfacelink.vmc

import kotlin.test.Test
import kotlin.test.assertEquals

class VmcHeadParamKeysTest {

    @Test
    fun vrm10KeysUseCamelCase() {
        val keys = VmcHeadParamKeys.VRM_1_0
        assertEquals("headYaw", keys.yaw)
        assertEquals("headPitch", keys.pitch)
        assertEquals("headRoll", keys.roll)
    }

    @Test
    fun vrm0xKeysUsePascalCase() {
        val keys = VmcHeadParamKeys.VRM_0_X
        assertEquals("HeadYaw", keys.yaw)
        assertEquals("HeadPitch", keys.pitch)
        assertEquals("HeadRoll", keys.roll)
    }

    @Test
    fun customKeysCanBeCreated() {
        val keys = VmcHeadParamKeys(yaw = "myYaw", pitch = "myPitch", roll = "myRoll")
        assertEquals("myYaw", keys.yaw)
        assertEquals("myPitch", keys.pitch)
        assertEquals("myRoll", keys.roll)
    }
}
