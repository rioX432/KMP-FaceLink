package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.util.Calibrator
import io.github.kmpfacelink.util.ExponentialMovingAverage
import io.github.kmpfacelink.util.OneEuroFilter
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Stress tests that verify thread safety of the processing pipeline.
 *
 * These tests simulate the concurrent access patterns that occur in production:
 * - Processing callback (camera thread) running calibrate + smooth
 * - UI thread calling resetCalibration / updateSmoothing
 */
@OptIn(ExperimentalAtomicApi::class)
class ThreadSafetyTest {

    private val iterations = 500

    private fun sampleBlendShapes(seed: Float = 0.5f): BlendShapeData =
        BlendShape.entries.associateWith { seed }

    @Test
    fun concurrentCalibrateAndReset() = runTest {
        val lock = PlatformLock()
        val calibrator = Calibrator()
        val failed = AtomicInt(0)

        val jobs = (1..iterations).map { i ->
            launch(Dispatchers.Default) {
                try {
                    if (i % 5 == 0) {
                        lock.withLock { calibrator.reset() }
                    } else {
                        val value = (i % 100) / 100f
                        lock.withLock { calibrator.calibrate(sampleBlendShapes(value)) }
                    }
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    failed.addAndFetch(1)
                    fail("Unexpected exception: ${e.message}")
                }
            }
        }
        jobs.joinAll()

        assertEquals(0, failed.load(), "Concurrent calibrate+reset should not throw")
    }

    @Test
    fun concurrentSmoothAndReset() = runTest {
        val lock = PlatformLock()
        val smoother = ExponentialMovingAverage(alpha = 0.5f)
        val failed = AtomicInt(0)

        val jobs = (1..iterations).map { i ->
            launch(Dispatchers.Default) {
                try {
                    if (i % 5 == 0) {
                        lock.withLock { smoother.reset() }
                    } else {
                        val ts = i.toLong()
                        val value = (i % 100) / 100f
                        lock.withLock { smoother.smooth(sampleBlendShapes(value), ts) }
                    }
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    failed.addAndFetch(1)
                    fail("Unexpected exception: ${e.message}")
                }
            }
        }
        jobs.joinAll()

        assertEquals(0, failed.load(), "Concurrent smooth+reset should not throw")
    }

    @Test
    fun smootherReplacementDuringSmoothing() = runTest {
        val lock = PlatformLock()
        var smoother: ExponentialMovingAverage? = ExponentialMovingAverage(alpha = 0.3f)
        val failed = AtomicInt(0)

        val jobs = (1..iterations).map { i ->
            launch(Dispatchers.Default) {
                try {
                    if (i % 10 == 0) {
                        // Simulate updateSmoothing
                        lock.withLock { smoother = ExponentialMovingAverage(alpha = 0.5f) }
                    } else {
                        val ts = i.toLong()
                        val value = (i % 100) / 100f
                        lock.withLock {
                            smoother?.smooth(sampleBlendShapes(value), ts)
                        }
                    }
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    failed.addAndFetch(1)
                    fail("Unexpected exception: ${e.message}")
                }
            }
        }
        jobs.joinAll()

        assertEquals(0, failed.load(), "Smoother replacement during smoothing should not throw")
    }

    @Test
    fun releasedFlagPreventsProcessing() = runTest {
        val lock = PlatformLock()
        val released = AtomicInt(0)
        val calibrator = Calibrator()
        val processedCount = AtomicInt(0)

        // Release immediately
        released.store(1)

        val jobs = (1..iterations).map {
            launch(Dispatchers.Default) {
                lock.withLock {
                    if (released.load() != 0) return@launch
                    calibrator.calibrate(sampleBlendShapes())
                    processedCount.addAndFetch(1)
                }
            }
        }
        jobs.joinAll()

        assertEquals(0, processedCount.load(), "No processing should occur after release")
    }

    @Test
    fun concurrentCalibrateAndSmoothWithOneEuro() = runTest {
        val lock = PlatformLock()
        val calibrator = Calibrator()
        val smoother = OneEuroFilter(minCutoff = 1.0f, beta = 0.007f, dCutoff = 1.0f)
        val failed = AtomicInt(0)

        val jobs = (1..iterations).map { i ->
            launch(Dispatchers.Default) {
                try {
                    val value = (i % 100) / 100f
                    val ts = i.toLong()
                    lock.withLock {
                        val calibrated = calibrator.calibrate(sampleBlendShapes(value))
                        smoother.smooth(calibrated, ts)
                    }
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    failed.addAndFetch(1)
                    fail("Unexpected exception: ${e.message}")
                }
            }
        }
        jobs.joinAll()

        assertEquals(0, failed.load(), "Concurrent calibrate+smooth pipeline should not throw")
    }

    @Test
    fun withLockIsReentrantSafe() {
        val lock = PlatformLock()
        var counter = 0

        lock.withLock {
            counter++
            // Reentrant call (our lock implementations are reentrant)
            lock.withLock {
                counter++
            }
        }

        assertEquals(2, counter, "Reentrant lock should allow nested withLock calls")
    }

    @Test
    fun releaseDuringActiveProcessing() = runTest {
        val lock = PlatformLock()
        val released = AtomicInt(0)
        val calibrator = Calibrator()
        val failed = AtomicInt(0)
        val completedBeforeRelease = AtomicInt(0)

        val jobs = (1..iterations).map { i ->
            launch(Dispatchers.Default) {
                try {
                    if (i == iterations / 2) {
                        // Simulate release mid-processing
                        released.store(1)
                        lock.withLock { calibrator.reset() }
                    } else {
                        lock.withLock {
                            if (released.load() != 0) return@launch
                            calibrator.calibrate(sampleBlendShapes((i % 100) / 100f))
                            completedBeforeRelease.addAndFetch(1)
                        }
                    }
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    failed.addAndFetch(1)
                    fail("Unexpected exception: ${e.message}")
                }
            }
        }
        jobs.joinAll()

        assertEquals(0, failed.load(), "Release during processing should not throw")
        assertTrue(
            completedBeforeRelease.load() < iterations,
            "Some iterations should be skipped after release",
        )
    }
}
