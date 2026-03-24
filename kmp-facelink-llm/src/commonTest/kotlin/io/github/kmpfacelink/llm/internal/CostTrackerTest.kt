package io.github.kmpfacelink.llm.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CostTrackerTest {

    @Test
    fun canAffordRequestWhenUnderBudget() {
        val tracker = CostTracker(
            dailyBudget = 1.0,
            costPerRequest = 0.1,
            timeProvider = { 0L },
        )

        assertTrue(tracker.canAffordRequest())
    }

    @Test
    fun cannotAffordRequestWhenOverBudget() {
        val tracker = CostTracker(
            dailyBudget = 0.3,
            costPerRequest = 0.1,
            timeProvider = { 0L },
        )

        repeat(3) { tracker.recordRequest() }
        assertFalse(tracker.canAffordRequest())
    }

    @Test
    fun unlimitedBudgetAlwaysAffordable() {
        val tracker = CostTracker(
            dailyBudget = 0.0,
            costPerRequest = 100.0,
            timeProvider = { 0L },
        )

        repeat(100) { tracker.recordRequest() }
        assertTrue(tracker.canAffordRequest())
    }

    @Test
    fun remainingBudgetDecreases() {
        val tracker = CostTracker(
            dailyBudget = 1.0,
            costPerRequest = 0.25,
            timeProvider = { 0L },
        )

        tracker.recordRequest()
        assertEquals(0.75, tracker.remainingBudget, 0.001)
    }

    @Test
    fun remainingBudgetUnlimited() {
        val tracker = CostTracker(
            dailyBudget = 0.0,
            costPerRequest = 0.1,
            timeProvider = { 0L },
        )

        assertEquals(Double.MAX_VALUE, tracker.remainingBudget)
    }

    @Test
    fun resetsOnNewDay() {
        var time = 0L
        val tracker = CostTracker(
            dailyBudget = 0.5,
            costPerRequest = 0.25,
            timeProvider = { time },
        )

        tracker.recordRequest()
        tracker.recordRequest()
        assertFalse(tracker.canAffordRequest())

        // Advance to next day (86400000 ms)
        time = 86_400_000L
        assertTrue(tracker.canAffordRequest())
        assertEquals(0.5, tracker.remainingBudget, 0.001)
    }

    @Test
    fun computeDayStartAlignsToDayBoundary() {
        // 1.5 days in ms
        val timestamp = 86_400_000L + 43_200_000L
        val dayStart = CostTracker.computeDayStart(timestamp)
        assertEquals(86_400_000L, dayStart)
    }

    @Test
    fun dailyCostFlowUpdates() {
        val tracker = CostTracker(
            dailyBudget = 1.0,
            costPerRequest = 0.1,
            timeProvider = { 0L },
        )

        assertEquals(0.0, tracker.dailyCost.value)
        tracker.recordRequest()
        assertEquals(0.1, tracker.dailyCost.value, 0.001)
    }
}
