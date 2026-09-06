package io.github.kmpfacelink.sample.ui

import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.HolisticTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.api.createHolisticTracker
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Swift-visible sample factories that preserve Kotlin constructor defaults.
 *
 * Kotlin default arguments are not available to every generated Swift initializer. Keeping the
 * defaults here prevents the sample wrapper from copying the SDK's full configuration surface.
 */
public fun createSampleFaceTrackerConfig(): FaceTrackerConfig =
    FaceTrackerConfig(smoothingConfig = SmoothingConfig.Ema(alpha = SAMPLE_EMA_ALPHA))

public fun createSampleHandTrackerConfig(): HandTrackerConfig =
    HandTrackerConfig(smoothingConfig = SmoothingConfig.Ema(alpha = SAMPLE_EMA_ALPHA))

public fun createSampleHolisticTrackerConfig(): HolisticTrackerConfig =
    HolisticTrackerConfig(
        enableFace = true,
        enableHand = true,
        enableBody = true,
        faceConfig = FaceTrackerConfig(smoothingConfig = SmoothingConfig.Ema(alpha = SAMPLE_EMA_ALPHA)),
        handConfig = HandTrackerConfig(smoothingConfig = SmoothingConfig.Ema(alpha = SAMPLE_EMA_ALPHA)),
        bodyConfig = BodyTrackerConfig(smoothingConfig = SmoothingConfig.Ema(alpha = SAMPLE_EMA_ALPHA)),
    )

public fun createSampleFaceTracker(): FaceTracker =
    createFaceTracker(PlatformContext(), createSampleFaceTrackerConfig())

public fun createSampleHandTracker(): HandTracker =
    createHandTracker(PlatformContext(), createSampleHandTrackerConfig())

public fun createSampleHolisticTracker(): HolisticTracker =
    createHolisticTracker(PlatformContext(), createSampleHolisticTrackerConfig())

public class IosFaceSampleSession {
    public val initialSmoothingConfig: SmoothingConfig =
        SmoothingConfig.Ema(alpha = SAMPLE_EMA_ALPHA)
    public val tracker: FaceTracker = createSampleFaceTracker()
    public val trackingDataState: MutableStateFlow<FaceTrackingData?> = MutableStateFlow(null)

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observation: Job? = null

    public suspend fun start() {
        if (observation != null) return
        observation = scope.launch { tracker.trackingData.collect { trackingDataState.value = it } }
        try {
            tracker.start()
        } catch (error: Throwable) {
            observation?.cancel()
            observation = null
            throw error
        }
    }

    public suspend fun stop() {
        tracker.stop()
        observation?.cancel()
        observation = null
    }

    public fun release() {
        observation?.cancel()
        observation = null
        scope.cancel()
        tracker.release()
    }
}

public class IosHandSampleSession {
    public val tracker: HandTracker = createSampleHandTracker()
    public val trackingDataState: MutableStateFlow<HandTrackingData?> = MutableStateFlow(null)

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observation: Job? = null

    public suspend fun start() {
        if (observation != null) return
        observation = scope.launch { tracker.trackingData.collect { trackingDataState.value = it } }
        try {
            tracker.start()
        } catch (error: Throwable) {
            observation?.cancel()
            observation = null
            throw error
        }
    }

    public suspend fun stop() {
        tracker.stop()
        observation?.cancel()
        observation = null
    }

    public fun release() {
        observation?.cancel()
        observation = null
        scope.cancel()
        tracker.release()
    }
}

private const val SAMPLE_EMA_ALPHA: Float = 0.4f
