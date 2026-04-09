package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.InferenceDelegate
import io.github.kmpfacelink.model.PowerMode
import io.github.kmpfacelink.model.ThermalState
import io.github.kmpfacelink.util.BatteryMonitor
import io.github.kmpfacelink.util.PowerModeManager
import io.github.kmpfacelink.util.ThermalMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fake thermal monitor for testing. */
private class FakeThermalMonitor : ThermalMonitor {
    private val _thermalState = MutableStateFlow(ThermalState.NOMINAL)
    override val thermalState: StateFlow<ThermalState> = _thermalState
    fun setState(state: ThermalState) { _thermalState.value = state }
    override fun release() { /* no-op */ }
}

/** Fake battery monitor for testing. */
private class FakeBatteryMonitor : BatteryMonitor {
    private val _batteryLevel = MutableStateFlow(1.0f)
    override val batteryLevel: StateFlow<Float> = _batteryLevel
    private val _isCharging = MutableStateFlow(false)
    override val isCharging: StateFlow<Boolean> = _isCharging
    fun setLevel(level: Float) { _batteryLevel.value = level }
    fun setCharging(charging: Boolean) { _isCharging.value = charging }
    override fun release() { /* no-op */ }
}

class PowerModeManagerTest {

    private val thermal = FakeThermalMonitor()
    private val battery = FakeBatteryMonitor()

    private fun createManager(
        mode: PowerMode = PowerMode.BALANCED,
        lowBatteryThreshold: Float = 0.15f,
        enableIdleDetection: Boolean = false,
    ) = PowerModeManager(
        powerMode = mode,
        thermalMonitor = thermal,
        batteryMonitor = battery,
        lowBatteryThreshold = lowBatteryThreshold,
        enableIdleDetection = enableIdleDetection,
    )

    @Test
    fun effectiveModeMatchesConfigWhenBatteryFull() {
        val manager = createManager(mode = PowerMode.HIGH)
        assertEquals(PowerMode.HIGH, manager.effectiveMode)
    }

    @Test
    fun effectiveModeDowngradesOnLowBattery() {
        val manager = createManager(mode = PowerMode.HIGH, lowBatteryThreshold = 0.2f)
        battery.setLevel(0.1f)
        assertEquals(PowerMode.BALANCED, manager.effectiveMode)
    }

    @Test
    fun effectiveModeDowngradesBalancedToLowPower() {
        val manager = createManager(mode = PowerMode.BALANCED)
        battery.setLevel(0.1f)
        assertEquals(PowerMode.LOW_POWER, manager.effectiveMode)
    }

    @Test
    fun lowPowerDoesNotDowngradeFurther() {
        val manager = createManager(mode = PowerMode.LOW_POWER)
        battery.setLevel(0.05f)
        assertEquals(PowerMode.LOW_POWER, manager.effectiveMode)
    }

    @Test
    fun chargingPreventsDowngrade() {
        val manager = createManager(mode = PowerMode.HIGH)
        battery.setLevel(0.1f)
        battery.setCharging(true)
        assertEquals(PowerMode.HIGH, manager.effectiveMode)
    }

    @Test
    fun effectiveFpsNominalBalanced() {
        val manager = createManager(mode = PowerMode.BALANCED)
        assertEquals(30, manager.effectiveFps)
    }

    @Test
    fun effectiveFpsHighNominal() {
        val manager = createManager(mode = PowerMode.HIGH)
        assertEquals(60, manager.effectiveFps)
    }

    @Test
    fun effectiveFpsReducedByThermalState() {
        val manager = createManager(mode = PowerMode.HIGH)
        thermal.setState(ThermalState.SERIOUS)
        assertEquals(30, manager.effectiveFps) // 60 / 2
    }

    @Test
    fun effectiveFpsCriticalHighMode() {
        val manager = createManager(mode = PowerMode.HIGH)
        thermal.setState(ThermalState.CRITICAL)
        assertEquals(15, manager.effectiveFps) // 60 / 4
    }

    @Test
    fun effectiveFpsBalancedFair() {
        val manager = createManager(mode = PowerMode.BALANCED)
        thermal.setState(ThermalState.FAIR)
        assertEquals(25, manager.effectiveFps) // 30 * 5/6
    }

    @Test
    fun effectiveFpsBalancedSerious() {
        val manager = createManager(mode = PowerMode.BALANCED)
        thermal.setState(ThermalState.SERIOUS)
        assertEquals(20, manager.effectiveFps) // 30 * 2/3
    }

    @Test
    fun effectiveFpsBalancedCritical() {
        val manager = createManager(mode = PowerMode.BALANCED)
        thermal.setState(ThermalState.CRITICAL)
        assertEquals(15, manager.effectiveFps) // 30 / 2
    }

    @Test
    fun effectiveFpsLowPowerCritical() {
        val manager = createManager(mode = PowerMode.LOW_POWER)
        thermal.setState(ThermalState.CRITICAL)
        assertEquals(10, manager.effectiveFps) // 15 * 2/3
    }

    @Test
    fun shouldProcessRespectsTargetFps() {
        val manager = createManager(mode = PowerMode.BALANCED)
        // 30fps = 33ms interval
        assertTrue(manager.shouldProcess(0L))
        assertFalse(manager.shouldProcess(10L))
        assertFalse(manager.shouldProcess(32L))
        assertTrue(manager.shouldProcess(33L))
    }

    @Test
    fun shouldDisableEnhancerInLowPowerMode() {
        val manager = createManager(mode = PowerMode.LOW_POWER)
        assertTrue(manager.shouldDisableEnhancer())
    }

    @Test
    fun shouldDisableEnhancerAtSeriousThermal() {
        val manager = createManager(mode = PowerMode.BALANCED)
        thermal.setState(ThermalState.SERIOUS)
        assertTrue(manager.shouldDisableEnhancer())
    }

    @Test
    fun shouldNotDisableEnhancerAtNominal() {
        val manager = createManager(mode = PowerMode.BALANCED)
        assertFalse(manager.shouldDisableEnhancer())
    }

    @Test
    fun idleDetectionReducesFps() {
        val manager = createManager(
            mode = PowerMode.BALANCED,
            enableIdleDetection = true,
        )
        val blendShapes = BlendShape.entries.associateWith { 0.1f }

        // Feed identical frames for 2+ seconds to trigger idle
        manager.onFrameProcessed(blendShapes, 0L)
        manager.onFrameProcessed(blendShapes, 1000L)
        manager.onFrameProcessed(blendShapes, 2000L)

        // When idle, FPS should be halved: 30/2 = 15
        assertEquals(15, manager.effectiveFps)
    }

    @Test
    fun downgradeModeFunction() {
        assertEquals(PowerMode.BALANCED, PowerModeManager.downgradeMode(PowerMode.HIGH))
        assertEquals(PowerMode.LOW_POWER, PowerModeManager.downgradeMode(PowerMode.BALANCED))
        assertEquals(PowerMode.LOW_POWER, PowerModeManager.downgradeMode(PowerMode.LOW_POWER))
    }

    @Test
    fun computeThermalAdjustedFpsHighNominal() {
        assertEquals(60, PowerModeManager.computeThermalAdjustedFps(PowerMode.HIGH, ThermalState.NOMINAL))
    }

    @Test
    fun computeThermalAdjustedFpsLowPowerNominal() {
        assertEquals(15, PowerModeManager.computeThermalAdjustedFps(PowerMode.LOW_POWER, ThermalState.NOMINAL))
    }

    // ── recommendDelegate tests ──

    @Test
    fun recommendDelegateNoNpuReturnsAuto() {
        assertEquals(
            InferenceDelegate.AUTO,
            PowerModeManager.recommendDelegate(PowerMode.BALANCED, ThermalState.NOMINAL, npuAvailable = false),
        )
    }

    @Test
    fun recommendDelegateLowPowerWithNpuReturnsNpu() {
        assertEquals(
            InferenceDelegate.NPU,
            PowerModeManager.recommendDelegate(PowerMode.LOW_POWER, ThermalState.NOMINAL, npuAvailable = true),
        )
    }

    @Test
    fun recommendDelegateBalancedWithNpuReturnsNpu() {
        assertEquals(
            InferenceDelegate.NPU,
            PowerModeManager.recommendDelegate(PowerMode.BALANCED, ThermalState.NOMINAL, npuAvailable = true),
        )
    }

    @Test
    fun recommendDelegateHighNominalWithNpuReturnsGpu() {
        assertEquals(
            InferenceDelegate.GPU,
            PowerModeManager.recommendDelegate(PowerMode.HIGH, ThermalState.NOMINAL, npuAvailable = true),
        )
    }

    @Test
    fun recommendDelegateHighSeriousWithNpuReturnsNpu() {
        assertEquals(
            InferenceDelegate.NPU,
            PowerModeManager.recommendDelegate(PowerMode.HIGH, ThermalState.SERIOUS, npuAvailable = true),
        )
    }

    @Test
    fun recommendDelegateHighCriticalWithNpuReturnsNpu() {
        assertEquals(
            InferenceDelegate.NPU,
            PowerModeManager.recommendDelegate(PowerMode.HIGH, ThermalState.CRITICAL, npuAvailable = true),
        )
    }

    @Test
    fun recommendDelegateHighFairWithNpuReturnsGpu() {
        assertEquals(
            InferenceDelegate.GPU,
            PowerModeManager.recommendDelegate(PowerMode.HIGH, ThermalState.FAIR, npuAvailable = true),
        )
    }

    @Test
    fun recommendedDelegateInstanceMethod() {
        val manager = PowerModeManager(
            powerMode = PowerMode.BALANCED,
            thermalMonitor = thermal,
            batteryMonitor = battery,
            npuAvailable = true,
        )
        assertEquals(InferenceDelegate.NPU, manager.recommendedDelegate())
    }

    @Test
    fun recommendedDelegateWithoutNpu() {
        val manager = createManager(mode = PowerMode.BALANCED)
        assertEquals(InferenceDelegate.AUTO, manager.recommendedDelegate())
    }
}
