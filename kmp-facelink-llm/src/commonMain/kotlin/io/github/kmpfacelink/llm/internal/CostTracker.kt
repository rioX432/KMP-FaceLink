package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.networking.currentTimeMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60
private const val MILLIS_PER_SECOND = 1000L
private const val MILLIS_PER_DAY =
    HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MILLIS_PER_SECOND

/**
 * Tracks daily cloud API cost and enforces a budget cap.
 *
 * Costs are accumulated per calendar day (UTC). When a new day begins,
 * the accumulated cost resets to zero.
 *
 * @param dailyBudget Maximum daily spend in USD. Zero means unlimited.
 * @param costPerRequest Estimated cost per cloud API request in USD.
 * @param timeProvider Function returning current time in milliseconds (injectable for tests).
 */
internal class CostTracker(
    private val dailyBudget: Double,
    private val costPerRequest: Double,
    private val timeProvider: () -> Long = { currentTimeMs() },
) {
    private var dayStartMs: Long = computeDayStart(timeProvider())
    private val _dailyCost = MutableStateFlow(0.0)

    /** Current accumulated daily cost in USD. */
    internal val dailyCost: StateFlow<Double> = _dailyCost.asStateFlow()

    /** Returns true if the budget allows another cloud request. */
    internal fun canAffordRequest(): Boolean {
        resetIfNewDay()
        if (dailyBudget <= 0.0) return true
        return _dailyCost.value + costPerRequest <= dailyBudget
    }

    /** Records a single cloud API request. */
    internal fun recordRequest() {
        resetIfNewDay()
        _dailyCost.value += costPerRequest
    }

    /** Remaining budget for today in USD. Returns [Double.MAX_VALUE] if unlimited. */
    internal val remainingBudget: Double
        get() {
            resetIfNewDay()
            if (dailyBudget <= 0.0) return Double.MAX_VALUE
            return (dailyBudget - _dailyCost.value).coerceAtLeast(0.0)
        }

    private fun resetIfNewDay() {
        val now = timeProvider()
        val currentDayStart = computeDayStart(now)
        if (currentDayStart > dayStartMs) {
            dayStartMs = currentDayStart
            _dailyCost.value = 0.0
        }
    }

    internal companion object {
        internal fun computeDayStart(timestampMs: Long): Long =
            (timestampMs / MILLIS_PER_DAY) * MILLIS_PER_DAY
    }
}
