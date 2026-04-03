package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [VmcSender] public API covering lifecycle,
 * restartability, frame sending, and release behavior.
 */
class VmcSenderTest {

    private val config = VmcConfig(
        host = VmcConfig.DEFAULT_HOST,
        port = TEST_PORT,
    )

    // --- Lifecycle: start / stop ---

    @Test
    fun startCollectsFromFlow() = runTest {
        val sender = VmcSender(config)
        val collected = CompletableDeferred<Boolean>()
        val trackingFlow = flow {
            emit(createTrackingData())
            collected.complete(true)
        }

        sender.start(trackingFlow, backgroundScope)
        assertTrue(collected.await(), "Flow should be collected after start")
        sender.stop()
    }

    @Test
    fun stopCancelsCollection() = runTest {
        val sender = VmcSender(config)
        val flow = MutableSharedFlow<FaceTrackingData>()
        var collecting = false

        val job = launch {
            sender.start(
                flow {
                    collecting = true
                    flow.collect { emit(it) }
                },
                this,
            )
        }
        // Let the coroutine start
        testScheduler.advanceUntilIdle()

        sender.stop()
        testScheduler.advanceUntilIdle()
        job.cancel()

        // After stop, the sender should be safe to use again
        sender.release()
    }

    @Test
    fun stopWithoutStartIsSafe() {
        val sender = VmcSender(config)
        // Should not throw
        sender.stop()
        sender.stop()
    }

    // --- Restartability (regression for #543) ---

    @Test
    fun startStopStartRestartsSuccessfully() = runTest {
        val sender = VmcSender(config)
        val firstCollected = CompletableDeferred<Boolean>()
        val secondCollected = CompletableDeferred<Boolean>()

        val flow1 = flow {
            emit(createTrackingData())
            firstCollected.complete(true)
        }
        val flow2 = flow {
            emit(createTrackingData())
            secondCollected.complete(true)
        }

        // First session
        sender.start(flow1, backgroundScope)
        assertTrue(firstCollected.await(), "First session should collect")
        sender.stop()

        // Second session after restart
        sender.start(flow2, backgroundScope)
        assertTrue(secondCollected.await(), "Second session should collect after restart")
        sender.stop()
    }

    @Test
    fun startWithExpressionsStopStartRestartsSuccessfully() = runTest {
        val sender = VmcSender(config)
        val firstCollected = CompletableDeferred<Boolean>()
        val secondCollected = CompletableDeferred<Boolean>()

        val flow1 = flow {
            emit(mapOf("happy" to 0.8f))
            firstCollected.complete(true)
        }
        val flow2 = flow {
            emit(mapOf("aa" to 0.5f))
            secondCollected.complete(true)
        }

        sender.startWithExpressions(flow1, backgroundScope)
        assertTrue(firstCollected.await())
        sender.stop()

        sender.startWithExpressions(flow2, backgroundScope)
        assertTrue(secondCollected.await())
        sender.stop()
    }

    // --- sendFrame ---

    @Test
    fun sendFrameWithTrackingData() {
        val sender = VmcSender(config)
        val blendShapes = mapOf(
            BlendShape.JAW_OPEN to 0.6f,
            BlendShape.MOUTH_SMILE_LEFT to 0.9f,
        )
        val head = HeadTransform(pitch = 10f, yaw = -5f, roll = 2f)

        // Should not throw — sends UDP packets to localhost
        sender.sendFrame(blendShapes, head, SAMPLE_TIMESTAMP_MS)
        sender.stop()
    }

    @Test
    fun sendFrameWithEmptyBlendShapes() {
        val sender = VmcSender(config)
        sender.sendFrame(emptyBlendShapeData(), HeadTransform(), SAMPLE_TIMESTAMP_MS)
        sender.stop()
    }

    // --- sendExpressionFrame ---

    @Test
    fun sendExpressionFrameWithDefaultKeys() {
        val sender = VmcSender(config)
        val expressions = mapOf(
            "happy" to 0.8f,
            "aa" to 0.5f,
            "headYaw" to -10f,
            "headPitch" to 15f,
            "headRoll" to 3f,
        )

        sender.sendExpressionFrame(expressions)
        sender.stop()
    }

    @Test
    fun sendExpressionFrameWithCustomHeadKeys() {
        val sender = VmcSender(config)
        val customKeys = VmcHeadParamKeys(
            yaw = "rotY",
            pitch = "rotX",
            roll = "rotZ",
        )
        val expressions = mapOf(
            "smile" to 0.7f,
            "rotY" to 5f,
            "rotX" to -3f,
            "rotZ" to 1f,
        )

        sender.sendExpressionFrame(expressions, customKeys)
        sender.stop()
    }

    @Test
    fun sendExpressionFrameWithEmptyMap() {
        val sender = VmcSender(config)
        sender.sendExpressionFrame(emptyMap())
        sender.stop()
    }

    // --- start skips non-tracking frames ---

    @Test
    fun startSkipsNonTrackingFrames() = runTest {
        val sender = VmcSender(config)
        val emitted = CompletableDeferred<Boolean>()

        val trackingFlow = flow {
            // Emit a non-tracking frame first
            emit(FaceTrackingData.notTracking())
            // Then a tracking frame
            emit(createTrackingData())
            emitted.complete(true)
        }

        sender.start(trackingFlow, backgroundScope)
        assertTrue(emitted.await(), "Flow should complete without error")
        sender.stop()
    }

    // --- startWithExpressions collects all frames ---

    @Test
    fun startWithExpressionsCollectsAllFrames() = runTest {
        val sender = VmcSender(config)
        var frameCount = 0
        val done = CompletableDeferred<Int>()

        val expressionFlow = flow {
            emit(mapOf("happy" to 0.5f))
            frameCount++
            emit(mapOf("aa" to 0.3f))
            frameCount++
            done.complete(frameCount)
        }

        sender.startWithExpressions(expressionFlow, backgroundScope)
        val count = done.await()
        assertTrue(count >= 2, "Should collect at least 2 expression frames")
        sender.stop()
    }

    // --- release ---

    @Test
    fun releaseStopsSender() = runTest {
        val sender = VmcSender(config)
        val flow = MutableSharedFlow<FaceTrackingData>()

        sender.start(flow, backgroundScope)
        sender.release()

        // After release, the collection job should be cancelled
        assertFalse(flow.subscriptionCount.value > 0, "No subscribers after release")
    }

    @Test
    fun doubleReleaseIsSafe() {
        val sender = VmcSender(config)
        sender.release()
        sender.release()
    }

    @Test
    fun releaseAfterStopIsSafe() {
        val sender = VmcSender(config)
        sender.stop()
        sender.release()
    }

    // --- Bundle vs individual message mode ---

    @Test
    fun sendFrameWithBundlesDisabled() {
        val noBundleConfig = VmcConfig(
            host = VmcConfig.DEFAULT_HOST,
            port = TEST_PORT,
            sendBundles = false,
        )
        val sender = VmcSender(noBundleConfig)
        val blendShapes = mapOf(BlendShape.EYE_BLINK_LEFT to 0.5f)

        sender.sendFrame(blendShapes, HeadTransform(), SAMPLE_TIMESTAMP_MS)
        sender.stop()
    }

    @Test
    fun sendExpressionFrameWithBundlesDisabled() {
        val noBundleConfig = VmcConfig(
            host = VmcConfig.DEFAULT_HOST,
            port = TEST_PORT,
            sendBundles = false,
        )
        val sender = VmcSender(noBundleConfig)
        sender.sendExpressionFrame(mapOf("happy" to 0.8f))
        sender.stop()
    }

    private fun createTrackingData(): FaceTrackingData = FaceTrackingData(
        blendShapes = mapOf(
            BlendShape.JAW_OPEN to 0.5f,
            BlendShape.EYE_BLINK_LEFT to 0.3f,
        ),
        headTransform = HeadTransform(pitch = 5f, yaw = -3f, roll = 1f),
        timestampMs = SAMPLE_TIMESTAMP_MS,
        isTracking = true,
    )
}

private const val TEST_PORT = 49539
private const val SAMPLE_TIMESTAMP_MS = 1000L
