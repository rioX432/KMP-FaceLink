package io.github.kmpfacelink.llm

import io.github.kmpfacelink.model.ThermalState
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

    @Test
    fun adaptiveHasDefaults() {
        val adaptive = RoutingStrategy.Adaptive()
        assertEquals(0.5f, adaptive.complexityThreshold)
        assertEquals(ThermalState.SERIOUS, adaptive.thermalThreshold)
        assertEquals(5000L, adaptive.latencyTimeoutMs)
        assertEquals(1.0, adaptive.dailyCostBudget, 0.001)
        assertEquals(0.003, adaptive.costPerCloudRequest, 0.0001)
    }

    @Test
    fun adaptiveAcceptsCustomValues() {
        val adaptive = RoutingStrategy.Adaptive(
            complexityThreshold = 0.7f,
            thermalThreshold = ThermalState.CRITICAL,
            latencyTimeoutMs = 10000L,
            dailyCostBudget = 5.0,
            costPerCloudRequest = 0.01,
        )
        assertEquals(0.7f, adaptive.complexityThreshold)
        assertEquals(ThermalState.CRITICAL, adaptive.thermalThreshold)
        assertEquals(10000L, adaptive.latencyTimeoutMs)
        assertEquals(5.0, adaptive.dailyCostBudget, 0.001)
        assertEquals(0.01, adaptive.costPerCloudRequest, 0.001)
    }

    @Test
    fun adaptiveDataClassEquality() {
        val a = RoutingStrategy.Adaptive(latencyTimeoutMs = 3000L)
        val b = RoutingStrategy.Adaptive(latencyTimeoutMs = 3000L)
        assertEquals(a, b)
    }
}
