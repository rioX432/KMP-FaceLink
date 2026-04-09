package io.github.kmpfacelink

import io.github.kmpfacelink.model.QualityPreset
import io.github.kmpfacelink.model.ThermalState
import io.github.kmpfacelink.util.QualityPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class QualityPolicyTest {

    @Test
    fun initialPresetIsPreserved() = runTest {
        val policy = QualityPolicy(QualityPreset.High, this)
        assertEquals(QualityPreset.High, policy.recommendedPreset.value)
    }

    @Test
    fun seriousThermalDowngradesImmediately() = runTest {
        val policy = QualityPolicy(QualityPreset.High, this)
        policy.onThermalStateChanged(ThermalState.SERIOUS)
        assertEquals(QualityPreset.Low, policy.recommendedPreset.value)
    }

    @Test
    fun criticalThermalDowngradesImmediately() = runTest {
        val policy = QualityPolicy(QualityPreset.High, this)
        policy.onThermalStateChanged(ThermalState.CRITICAL)
        assertEquals(QualityPreset.Low, policy.recommendedPreset.value)
    }

    @Test
    fun fairThermalDowngradesAfterDelay() = runTest {
        val policy = QualityPolicy(QualityPreset.High, this)

        policy.onThermalStateChanged(ThermalState.FAIR)
        // Not yet downgraded
        assertEquals(QualityPreset.High, policy.recommendedPreset.value)

        advanceTimeBy(QualityPolicy.DOWNGRADE_DELAY_MS + 1)
        assertEquals(QualityPreset.Medium, policy.recommendedPreset.value)
    }

    @Test
    fun fairThermalDowngradeCancelledByNominal() = runTest {
        val policy = QualityPolicy(QualityPreset.High, this)

        policy.onThermalStateChanged(ThermalState.FAIR)
        advanceTimeBy(QualityPolicy.DOWNGRADE_DELAY_MS / 2)
        // Cancel pending downgrade by returning to NOMINAL
        policy.onThermalStateChanged(ThermalState.NOMINAL)

        advanceTimeBy(QualityPolicy.DOWNGRADE_DELAY_MS)
        // Should still be High (downgrade was cancelled, upgrade not yet due)
        assertEquals(QualityPreset.High, policy.recommendedPreset.value)
    }

    @Test
    fun upgradeAfterNominalDelay() = runTest {
        val policy = QualityPolicy(QualityPreset.High, this)

        // First downgrade to Low
        policy.onThermalStateChanged(ThermalState.SERIOUS)
        assertEquals(QualityPreset.Low, policy.recommendedPreset.value)

        // Return to NOMINAL — should upgrade after delay
        policy.onThermalStateChanged(ThermalState.NOMINAL)
        assertEquals(QualityPreset.Low, policy.recommendedPreset.value)

        advanceTimeBy(QualityPolicy.UPGRADE_DELAY_MS + 1)
        assertEquals(QualityPreset.High, policy.recommendedPreset.value)
    }

    @Test
    fun upgradeIsCancelledByNewThermalPressure() = runTest {
        val policy = QualityPolicy(QualityPreset.High, this)

        policy.onThermalStateChanged(ThermalState.SERIOUS)
        assertEquals(QualityPreset.Low, policy.recommendedPreset.value)

        // Start upgrade
        policy.onThermalStateChanged(ThermalState.NOMINAL)
        advanceTimeBy(QualityPolicy.UPGRADE_DELAY_MS / 2)

        // New thermal pressure cancels upgrade
        policy.onThermalStateChanged(ThermalState.SERIOUS)
        assertEquals(QualityPreset.Low, policy.recommendedPreset.value)

        advanceTimeBy(QualityPolicy.UPGRADE_DELAY_MS)
        assertEquals(QualityPreset.Low, policy.recommendedPreset.value)
    }

    @Test
    fun noChangeWhenAlreadyAtTargetPreset() = runTest {
        val policy = QualityPolicy(QualityPreset.Low, this)
        policy.onThermalStateChanged(ThermalState.SERIOUS)
        assertEquals(QualityPreset.Low, policy.recommendedPreset.value)
    }

    @Test
    fun mapThermalToPresetNominal() {
        assertEquals(QualityPreset.High, QualityPolicy.mapThermalToPreset(ThermalState.NOMINAL))
    }

    @Test
    fun mapThermalToPresetFair() {
        assertEquals(QualityPreset.Medium, QualityPolicy.mapThermalToPreset(ThermalState.FAIR))
    }

    @Test
    fun mapThermalToPresetSerious() {
        assertEquals(QualityPreset.Low, QualityPolicy.mapThermalToPreset(ThermalState.SERIOUS))
    }

    @Test
    fun mapThermalToPresetCritical() {
        assertEquals(QualityPreset.Low, QualityPolicy.mapThermalToPreset(ThermalState.CRITICAL))
    }

    @Test
    fun presetOrdinalOrder() {
        assertEquals(0, QualityPolicy.presetOrdinal(QualityPreset.Low))
        assertEquals(1, QualityPolicy.presetOrdinal(QualityPreset.Medium))
        assertEquals(2, QualityPolicy.presetOrdinal(QualityPreset.High))
    }

    @Test
    fun cancelStopsPendingTransition() = runTest {
        val policy = QualityPolicy(QualityPreset.High, this)

        policy.onThermalStateChanged(ThermalState.FAIR)
        policy.cancel()

        advanceTimeBy(QualityPolicy.DOWNGRADE_DELAY_MS + 1)
        assertEquals(QualityPreset.High, policy.recommendedPreset.value)
    }

    @Test
    fun mediumPresetStartDowngradesToLowOnSerious() = runTest {
        val policy = QualityPolicy(QualityPreset.Medium, this)
        policy.onThermalStateChanged(ThermalState.SERIOUS)
        assertEquals(QualityPreset.Low, policy.recommendedPreset.value)
    }

    @Test
    fun mediumPresetUpgradesToHighOnNominalAfterDelay() = runTest {
        val policy = QualityPolicy(QualityPreset.Medium, this)

        policy.onThermalStateChanged(ThermalState.NOMINAL)
        assertEquals(QualityPreset.Medium, policy.recommendedPreset.value)

        advanceTimeBy(QualityPolicy.UPGRADE_DELAY_MS + 1)
        assertEquals(QualityPreset.High, policy.recommendedPreset.value)
    }
}
