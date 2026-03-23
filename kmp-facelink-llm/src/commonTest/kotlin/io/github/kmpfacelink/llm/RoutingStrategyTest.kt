package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoutingStrategyTest {

    @Test
    fun alwaysOnDeviceIsSingleton() {
        assertIs<RoutingStrategy.AlwaysOnDevice>(RoutingStrategy.AlwaysOnDevice)
    }

    @Test
    fun alwaysCloudIsSingleton() {
        assertIs<RoutingStrategy.AlwaysCloud>(RoutingStrategy.AlwaysCloud)
    }

    @Test
    fun autoHasDefaultThreshold() {
        val auto = RoutingStrategy.Auto()
        assertEquals(0.5f, auto.complexityThreshold)
    }

    @Test
    fun autoAcceptsCustomThreshold() {
        val auto = RoutingStrategy.Auto(complexityThreshold = 0.8f)
        assertEquals(0.8f, auto.complexityThreshold)
    }

    @Test
    fun autoDataClassEquality() {
        val a = RoutingStrategy.Auto(complexityThreshold = 0.3f)
        val b = RoutingStrategy.Auto(complexityThreshold = 0.3f)
        assertEquals(a, b)
    }
}
