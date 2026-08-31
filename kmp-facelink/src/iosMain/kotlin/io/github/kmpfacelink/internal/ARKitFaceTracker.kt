@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.CalibrationPhase
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceObservationProvenance
import io.github.kmpfacelink.model.FacePipelineKind
import io.github.kmpfacelink.model.FacePipelinePerformanceClock
import io.github.kmpfacelink.model.FacePipelinePerformanceEvent
import io.github.kmpfacelink.model.FacePipelinePerformanceOutcome
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.InferenceMaterialization
import io.github.kmpfacelink.model.QualityPreset
import io.github.kmpfacelink.model.SessionInstanceId
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedFace
import io.github.kmpfacelink.model.TrackingContextEpoch
import io.github.kmpfacelink.model.filterByActive
import io.github.kmpfacelink.util.BlendShapeInterpolator
import io.github.kmpfacelink.util.BlendShapeMapPool
import io.github.kmpfacelink.util.BlendShapeSmoother
import io.github.kmpfacelink.util.Calibrator
import io.github.kmpfacelink.util.ConfidenceFrameStrategy
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameDecision
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.GazeEstimator
import io.github.kmpfacelink.util.PowerModeManager
import io.github.kmpfacelink.util.TransformUtils
import io.github.kmpfacelink.util.createFaceSmoother
import io.github.kmpfacelink.util.createSmoother
import io.github.kmpfacelink.util.withLock
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.get
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import platform.ARKit.ARFaceAnchor
import platform.ARKit.ARFaceTrackingConfiguration
import platform.ARKit.ARSession
import platform.ARKit.ARSessionDelegateProtocol
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.darwin.NSObject
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Suppress(
    "TooManyFunctions", // Interface overrides + thermal quality policy helpers
    "TooGenericExceptionCaught", // Objective-C callbacks must not let any Kotlin throwable escape.
)
@OptIn(ExperimentalAtomicApi::class)
internal class ARKitFaceTracker(
    private val config: FaceTrackerConfig,
    private val sessionInstanceId: SessionInstanceId = newIosSessionInstanceId(),
    private val monotonicNowNs: () -> Long = {
        (NSProcessInfo.processInfo.systemUptime * NANOS_PER_SECOND).toLong()
    },
) : BaseTracker<FaceTrackingData>(
    thermalMonitor = IosThermalMonitor(),
    frameThrottler = FrameThrottler(config.powerConfig.thermalPolicy),
),
    FaceTracker {

    override val trackingData: Flow<FaceTrackingData> = super.trackingData
    override val state: StateFlow<TrackingState> = super.state
    override val errorMessage: StateFlow<String?> = super.errorMessage
    private val mutablePerformanceEvents: MutableSharedFlow<FacePipelinePerformanceEvent>? =
        if (config.performanceInstrumentation.enabled) {
            MutableSharedFlow(extraBufferCapacity = config.performanceInstrumentation.eventBufferCapacity)
        } else {
            null
        }
    override val performanceEvents: Flow<FacePipelinePerformanceEvent> =
        mutablePerformanceEvents?.asSharedFlow() ?: emptyFlow()
    private val performanceBuffer = FacePipelinePerformanceBuffer(
        config.performanceInstrumentation.eventBufferCapacity,
    )
    private val callbackBarrier = CallbackLifecycleBarrier()

    override fun performanceEventsSnapshot(): List<FacePipelinePerformanceEvent> =
        performanceBuffer.snapshot()

    private var smoother: BlendShapeSmoother? = config.createSmoother()
    private var currentSmoothingConfig: SmoothingConfig = config.smoothingConfig
    private val calibrator = Calibrator(
        warmupFrames = config.calibrationConfig.warmupFrames,
        decayRate = config.calibrationConfig.rangeDecayRate,
    )

    private val batteryMonitor = IosBatteryMonitor()
    private val powerModeManager: PowerModeManager? = config.powerConfig.powerMode?.let { mode ->
        PowerModeManager(
            powerMode = mode,
            thermalMonitor = thermalMonitor,
            batteryMonitor = batteryMonitor,
            lowBatteryThreshold = config.powerConfig.lowBatteryThreshold,
            enableIdleDetection = config.powerConfig.enableIdleDetection,
        )
    }

    private var qualityScope: CoroutineScope? = null

    private val frameSkipStrategy = ConfidenceFrameStrategy(config.frameSkipConfig)
    private val interpolator = BlendShapeInterpolator()
    private var lastConfidence: Float = 0f
    private var nextEpochId: ULong = 1u
    private var currentEpoch: TrackingContextEpoch? = null
    private var interpolationEpoch: TrackingContextEpoch? = null
    private var interpolationProvenance: FaceObservationProvenance? = null
    private var calibrationFrameCount: ULong = 0u
    private var calibrationStateRevision: ULong = 0u
    private var processingStateRevision: ULong = 0u
    private var nextAnchorBatchSequence: ULong = 1u
    private var nextCallbackSequence: ULong = 1u

    // Pre-allocated scratch map for mapARKitBlendShapes — reused each frame.
    // Callers that store the result must copy via HashMap(result).
    private val blendShapePool = BlendShapeMapPool()
    private val blendShapeScratch: HashMap<BlendShape, Float> = blendShapePool.create()

    // Pre-allocated FloatArray for the 4x4 transform matrix — reused each frame.
    private val transformMatrix = FloatArray(16)

    private data class ProcessedFrame(
        val blendShapes: BlendShapeData,
        val provenance: FaceObservationProvenance,
    )

    private data class CallbackMeasurementContext(
        val anchorBatchSequence: ULong,
        val callbackSequence: ULong,
        val callbackStartedNs: Long,
    )

    /**
     * The underlying ARSession, exposed for camera preview sharing.
     * Only non-null after [start] has been called.
     */
    internal var arSession: ARSession? = null
        private set
    private val sessionDelegate = FaceTrackingDelegate()

    override suspend fun start() {
        pipelineLock.withLock {
            if (!guardStart()) return
        }

        if (!ARFaceTrackingConfiguration.isSupported) {
            setError("ARFaceTracking is not supported on this device")
            throw UnsupportedOperationException("ARFaceTracking is not supported on this device")
        }

        // Reuse existing session on restart, or create a new one
        val session = arSession ?: ARSession().also {
            it.delegate = sessionDelegate
            arSession = it
        }

        val configuration = ARFaceTrackingConfiguration().apply {
            lightEstimationEnabled = false
        }
        pipelineLock.withLock { rotateEpochLocked() }
        callbackBarrier.open()
        session.runWithConfiguration(configuration)

        pipelineLock.withLock { transitionToTracking() }
        startQualityPolicyCollection(session)
    }

    override suspend fun stop() {
        stopQualityPolicyCollection()
        arSession?.pause()
        // ARSession.pause() prevents new work; the barrier waits for a callback already inside KMP
        // so a post-stop snapshot cannot miss a tail event.
        callbackBarrier.closeAndDrain()
        pipelineLock.withLock {
            currentEpoch = null
            resetInterpolationLocked()
            mutableState.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        stopQualityPolicyCollection()
        arSession?.pause()
        callbackBarrier.closeAndDrain()
        pipelineLock.withLock {
            released.store(1)
            arSession?.delegate = null
            arSession = null
            currentEpoch = null
            resetInterpolationLocked()
            thermalMonitor.release()
            batteryMonitor.release()
            powerModeManager?.release()
            mutableState.value = TrackingState.RELEASED
        }
    }

    override fun resetCalibration() {
        pipelineLock.withLock {
            checkNotReleased("reset calibration")
            calibrator.reset()
            calibrationFrameCount = 0u
            calibrationStateRevision++
            smoother?.reset()
            processingStateRevision++
            forceRotateEpochLocked()
        }
    }

    override fun resetSmoothing() {
        pipelineLock.withLock {
            checkNotReleased("reset smoothing")
            smoother?.reset()
            processingStateRevision++
            forceRotateEpochLocked()
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            checkNotReleased("update smoothing")
            smoother = config.createFaceSmoother()
            currentSmoothingConfig = config
            processingStateRevision++
            forceRotateEpochLocked()
        }
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        pipelineLock.withLock { checkNotReleased("switch camera") }
        if (facing == CameraFacing.BACK) {
            throw UnsupportedOperationException(
                "The KMP-FaceLink ARKit face tracker uses the front-facing camera",
            )
        }
        // Already using front camera — no-op
    }

    /** Whether a frame at the given timestamp should be processed. */
    private fun shouldProcessFrame(timestampMs: Long): Boolean {
        return if (powerModeManager != null) {
            powerModeManager.shouldProcess(timestampMs)
        } else {
            frameThrottler.shouldProcess(timestampMs, thermalMonitor.thermalState.value)
        }
    }

    private fun processFaceAnchor(
        faceAnchor: ARFaceAnchor,
        measurement: CallbackMeasurementContext,
    ) {
        val epoch = pipelineLock.withLock { currentEpoch }
        if (epoch == null) {
            emitDropPerformance(
                measurement.anchorBatchSequence,
                measurement.callbackSequence,
                FacePipelinePerformanceOutcome.DROPPED_NO_ACTIVE_EPOCH,
            )
            return
        }
        val timestampMs = (arSession?.currentFrame?.timestamp?.times(1000))?.toLong()
            ?: monotonicNowNs() / NANOS_PER_MILLISECOND

        // Frame throttling: use PowerModeManager when available, else legacy FrameThrottler
        if (!shouldProcessFrame(timestampMs)) {
            emitDropPerformance(
                measurement.anchorBatchSequence,
                measurement.callbackSequence,
                FacePipelinePerformanceOutcome.DROPPED_THROTTLED,
                epoch,
            )
            return
        }

        // Confidence-guided skip: emit interpolated data for stable frames
        val decision = frameSkipStrategy.evaluate(lastConfidence)
        if (decision == FrameDecision.SKIP) {
            val emitted = emitInterpolatedFrame(
                timestampMs = timestampMs,
                epoch = epoch,
                measurement = measurement,
            )
            if (!emitted) {
                emitDropPerformance(
                    measurement.anchorBatchSequence,
                    measurement.callbackSequence,
                    FacePipelinePerformanceOutcome.DROPPED_INTERPOLATION_UNAVAILABLE,
                    epoch,
                )
            }
            return
        }

        // Full processing path
        val faceConfidence = if (faceAnchor.isTracked) 1.0f else 0.0f
        processFullFrame(
            faceAnchor,
            timestampMs,
            faceConfidence,
            epoch,
            measurement,
        )
    }

    @Suppress("LongMethod")
    private fun processFullFrame(
        faceAnchor: ARFaceAnchor,
        timestampMs: Long,
        faceConfidence: Float,
        epoch: TrackingContextEpoch,
        measurement: CallbackMeasurementContext,
    ) {
        @Suppress("UNCHECKED_CAST")
        val rawBlendShapes = faceAnchor.blendShapes as? Map<String, NSNumber> ?: emptyMap()
        var blendShapes: BlendShapeData = mapARKitBlendShapes(rawBlendShapes)
        val headTransform = extractTransform(faceAnchor)
        val rawExtractionEndedNs = monotonicNowNs()

        val processed = pipelineLock.withLock {
            if (isReleased()) return@withLock null
            if (currentEpoch?.epochFingerprint != epoch.epochFingerprint) return@withLock null

            if (config.calibrationConfig.enabled) {
                blendShapes = calibrator.calibrate(blendShapes)
                calibrationFrameCount++
                calibrationStateRevision++
            }
            smoother?.let { blendShapes = it.smooth(blendShapes, timestampMs) }
            powerModeManager?.onFrameProcessed(blendShapes, timestampMs)
            val emitted = HashMap(blendShapes).filterByActive(config.activeBlendShapes)
            val processedFrame = ProcessedFrame(
                blendShapes = emitted,
                provenance = FaceObservationProvenance(
                    contextEpochId = epoch.epochId,
                    faceIndex = 0,
                    pipelineKind = FacePipelineKind.PRIMARY,
                    appliedTransforms = effectiveConfig().fullPrimaryAppliedTransforms(enhancerApplied = false),
                    calibrationPhase = calibrationPhase(),
                    calibrationStateRevision = calibrationStateRevision,
                    processingStateRevision = processingStateRevision,
                    materialization = InferenceMaterialization.FULL_INFERENCE,
                    emittedBlendShapes = emitted.keys.toSet(),
                ),
            )
            lastConfidence = faceConfidence
            interpolator.onFrameProcessed(emitted, headTransform, timestampMs)
            frameSkipStrategy.onFrameProcessed(emitted)
            interpolationEpoch = epoch
            interpolationProvenance = processedFrame.provenance
            processedFrame
        }
        if (processed == null) {
            emitDropPerformance(
                measurement.anchorBatchSequence,
                measurement.callbackSequence,
                FacePipelinePerformanceOutcome.DROPPED_EPOCH_CHANGED,
                epoch,
            )
            return
        }
        val processedBlendShapes = processed.blendShapes

        val gazeData = GazeEstimator.estimate(processedBlendShapes)
        val provenance = processed.provenance

        val trackedFace = TrackedFace(
            faceIndex = 0,
            blendShapes = processedBlendShapes,
            headTransform = headTransform,
            gazeData = gazeData,
            confidence = faceConfidence,
            provenance = provenance,
        )

        val data = FaceTrackingData(
            blendShapes = processedBlendShapes,
            headTransform = headTransform,
            gazeData = gazeData,
            timestampMs = timestampMs,
            isTracking = true,
            faces = listOf(trackedFace),
            thermalState = thermalMonitor.thermalState.value,
            contextEpoch = epoch,
        )
        val outputReadyNs = monotonicNowNs()
        val enqueueAccepted = mutableTrackingData.tryEmit(data)
        val enqueueReturnedNs = monotonicNowNs()
        emitPerformance(
            FacePipelinePerformanceEvent(
                callbackSequence = measurement.callbackSequence,
                anchorBatchSequence = measurement.anchorBatchSequence,
                clock = FacePipelinePerformanceClock.IOS_MONOTONIC_CALLBACK_CLOCK,
                outcome = FacePipelinePerformanceOutcome.FULL_OUTPUT,
                materialization = InferenceMaterialization.FULL_INFERENCE,
                configurationFingerprint = epoch.configurationFingerprint,
                epochFingerprint = epoch.epochFingerprint,
                callbackToRawExtractionNs = rawExtractionEndedNs - measurement.callbackStartedNs,
                rawExtractionToOutputReadyNs = outputReadyNs - rawExtractionEndedNs,
                callbackToOutputReadyNs = outputReadyNs - measurement.callbackStartedNs,
                outputReadyToEnqueueReturnNs = enqueueReturnedNs - outputReadyNs,
                callbackToEnqueueReturnNs = enqueueReturnedNs - measurement.callbackStartedNs,
                enqueueAccepted = enqueueAccepted,
            ),
        )
    }

    private fun emitInterpolatedFrame(
        timestampMs: Long,
        epoch: TrackingContextEpoch,
        measurement: CallbackMeasurementContext,
    ): Boolean {
        val data = pipelineLock.withLock {
            if (currentEpoch?.epochFingerprint != epoch.epochFingerprint) return@withLock null
            if (interpolationEpoch?.epochFingerprint != epoch.epochFingerprint) return@withLock null
            val sourceProvenance = interpolationProvenance ?: return@withLock null
            val blendShapes = interpolator.interpolateBlendShapes(timestampMs) ?: return@withLock null
            val headTransform = interpolator.interpolateTransform(timestampMs) ?: return@withLock null
            val filtered = blendShapes.filterByActive(config.activeBlendShapes)
            val gazeData = GazeEstimator.estimate(filtered)
            val provenance = sourceProvenance.copy(
                appliedTransforms = sourceProvenance.appliedTransforms +
                    listOfNotNull(effectiveConfig().interpolatorAppliedTransform()),
                materialization = InferenceMaterialization.INTERPOLATED,
                emittedBlendShapes = filtered.keys.toSet(),
            )

            FaceTrackingData(
                blendShapes = filtered,
                headTransform = headTransform,
                gazeData = gazeData,
                timestampMs = timestampMs,
                isTracking = true,
                faces = listOf(
                    TrackedFace(
                        faceIndex = 0,
                        blendShapes = filtered,
                        headTransform = headTransform,
                        gazeData = gazeData,
                        confidence = lastConfidence,
                        provenance = provenance,
                    ),
                ),
                thermalState = thermalMonitor.thermalState.value,
                contextEpoch = epoch,
            )
        }
        if (data == null) return false
        val outputReadyNs = monotonicNowNs()
        val enqueueAccepted = mutableTrackingData.tryEmit(data)
        val enqueueReturnedNs = monotonicNowNs()
        emitPerformance(
            FacePipelinePerformanceEvent(
                callbackSequence = measurement.callbackSequence,
                anchorBatchSequence = measurement.anchorBatchSequence,
                clock = FacePipelinePerformanceClock.IOS_MONOTONIC_CALLBACK_CLOCK,
                outcome = FacePipelinePerformanceOutcome.INTERPOLATED_OUTPUT,
                materialization = InferenceMaterialization.INTERPOLATED,
                configurationFingerprint = epoch.configurationFingerprint,
                epochFingerprint = epoch.epochFingerprint,
                callbackToRawExtractionNs = null,
                rawExtractionToOutputReadyNs = null,
                callbackToOutputReadyNs = outputReadyNs - measurement.callbackStartedNs,
                outputReadyToEnqueueReturnNs = enqueueReturnedNs - outputReadyNs,
                callbackToEnqueueReturnNs = enqueueReturnedNs - measurement.callbackStartedNs,
                enqueueAccepted = enqueueAccepted,
            ),
        )
        return true
    }

    private fun startQualityPolicyCollection(session: ARSession) {
        if (powerModeManager == null) return
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        qualityScope = scope

        val initialPreset = QualityPreset.High // iOS always runs at full resolution
        powerModeManager.startQualityPolicy(initialPreset, scope)

        // On iOS, only frame rate can be adjusted (ARKit doesn't support mid-session resolution changes)
        powerModeManager.recommendedPreset
            ?.drop(1)
            ?.map { it.powerMode.baseFps }
            ?.distinctUntilChanged()
            ?.onEach { targetFps ->
                FaceLinkLogger.d(TAG, "Thermal quality change: preferredFrameRate -> $targetFps")
                session.currentFrame?.camera // Just to verify session is active
                val newConfig = ARFaceTrackingConfiguration().apply {
                    lightEstimationEnabled = false
                }
                // ARKit adjusts framerate through session configuration
                session.runWithConfiguration(newConfig)
            }
            ?.launchIn(scope)
    }

    private fun stopQualityPolicyCollection() {
        qualityScope?.cancel()
        qualityScope = null
    }

    private fun calibrationPhase(): CalibrationPhase = when {
        !config.calibrationConfig.enabled -> CalibrationPhase.DISABLED
        calibrationFrameCount <= config.calibrationConfig.warmupFrames.toULong() -> CalibrationPhase.WARMUP
        else -> CalibrationPhase.ACTIVE
    }

    private fun effectiveConfig(): FaceTrackerConfig = config.copy(smoothingConfig = currentSmoothingConfig)

    private fun rotateEpochLocked(): TrackingContextEpoch {
        val epoch = buildIosTrackingEpoch(
            epochId = nextEpochId++,
            sessionInstanceId = sessionInstanceId,
            config = effectiveConfig(),
        )
        currentEpoch = epoch
        resetInterpolationLocked()
        interpolationEpoch = epoch
        return epoch
    }

    private fun forceRotateEpochLocked() {
        if (currentEpoch != null) rotateEpochLocked()
    }

    private fun resetInterpolationLocked() {
        interpolationEpoch = null
        interpolationProvenance = null
        lastConfidence = 0f
        interpolator.reset()
        frameSkipStrategy.reset()
    }

    private fun mapARKitBlendShapes(rawBlendShapes: Map<String, NSNumber>): BlendShapeData {
        blendShapeScratch.clear()

        for ((key, value) in rawBlendShapes) {
            val shape = BlendShape.fromArKitName(key) ?: continue
            blendShapeScratch[shape] = value.floatValue.coerceIn(0f, 1f)
        }

        // Fill missing shapes
        for (shape in BlendShape.entries) {
            if (shape !in blendShapeScratch) {
                blendShapeScratch[shape] = 0f
            }
        }
        return blendShapeScratch
    }

    private fun extractTransform(faceAnchor: ARFaceAnchor): HeadTransform {
        // simd_float4x4 is 16 contiguous floats in memory (column-major order).
        // Use raw pointer reinterpretation since Kotlin/Native cinterop
        // does not expose simd_float4x4 struct fields.
        // Reuse the pre-allocated transformMatrix array to avoid a FloatArray allocation per frame.
        faceAnchor.transform.useContents {
            val floatPtr = this.ptr.reinterpret<FloatVar>()
            for (i in transformMatrix.indices) {
                transformMatrix[i] = floatPtr[i]
            }
        }
        return TransformUtils.fromMatrix(transformMatrix)
    }

    private fun emitPerformance(event: FacePipelinePerformanceEvent) {
        // A full buffer creates a visible callback-sequence gap in the collector, which makes the
        // aggregate run fail closed instead of silently claiming complete accounting.
        val events = mutablePerformanceEvents ?: return
        performanceBuffer.append(event)
        events.tryEmit(event)
    }

    private fun emitDropPerformance(
        anchorBatchSequence: ULong,
        callbackSequence: ULong,
        outcome: FacePipelinePerformanceOutcome,
        epoch: TrackingContextEpoch? = null,
    ) {
        emitPerformance(
            FacePipelinePerformanceEvent(
                callbackSequence = callbackSequence,
                anchorBatchSequence = anchorBatchSequence,
                clock = FacePipelinePerformanceClock.IOS_MONOTONIC_CALLBACK_CLOCK,
                outcome = outcome,
                materialization = null,
                configurationFingerprint = epoch?.configurationFingerprint,
                epochFingerprint = epoch?.epochFingerprint,
                callbackToRawExtractionNs = null,
                rawExtractionToOutputReadyNs = null,
                callbackToOutputReadyNs = null,
                outputReadyToEnqueueReturnNs = null,
                callbackToEnqueueReturnNs = null,
                enqueueAccepted = null,
            ),
        )
    }

    private companion object {
        private const val TAG = "ARKitFaceTracker"
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }

    private inner class FaceTrackingDelegate : NSObject(), ARSessionDelegateProtocol {
        @ObjCSignatureOverride
        override fun session(session: ARSession, didUpdateAnchors: List<*>) {
            // This must be the first callback statement: lock contention is part of the
            // instrumented KMP callback path rather than silently excluded from it.
            val callbackEnteredNs = monotonicNowNs()
            callbackBarrier.inCallback(callbackEnteredNs) { enteredNs ->
                val anchorBatchSequence = nextAnchorBatchSequence++
                for (anchor in didUpdateAnchors) {
                    if (anchor is ARFaceAnchor) {
                        val measurement = CallbackMeasurementContext(
                            anchorBatchSequence = anchorBatchSequence,
                            callbackSequence = nextCallbackSequence++,
                            callbackStartedNs = enteredNs,
                        )
                        try {
                            processFaceAnchor(
                                faceAnchor = anchor,
                                measurement = measurement,
                            )
                        } catch (failure: Throwable) {
                            // The terminal failure receives the already allocated sequence, so an
                            // otherwise complete prefix can never look like a valid aggregate.
                            emitPerformance(
                                FacePipelinePerformanceEvent(
                                    callbackSequence = measurement.callbackSequence,
                                    anchorBatchSequence = measurement.anchorBatchSequence,
                                    clock = FacePipelinePerformanceClock.IOS_MONOTONIC_CALLBACK_CLOCK,
                                    outcome = FacePipelinePerformanceOutcome.CALLBACK_FAILURE,
                                    materialization = null,
                                    configurationFingerprint = null,
                                    epochFingerprint = null,
                                    callbackToRawExtractionNs = null,
                                    rawExtractionToOutputReadyNs = null,
                                    callbackToOutputReadyNs = null,
                                    outputReadyToEnqueueReturnNs = null,
                                    callbackToEnqueueReturnNs = null,
                                    enqueueAccepted = null,
                                ),
                            )
                            setError(
                                "ARKit callback processing failed: " +
                                    (failure.message ?: failure::class.simpleName),
                            )
                            this@ARKitFaceTracker.arSession?.pause()
                        }
                        break // Only process first face
                    }
                }
            }
        }

        @ObjCSignatureOverride
        override fun session(session: ARSession, didRemoveAnchors: List<*>) {
            for (anchor in didRemoveAnchors) {
                if (anchor is ARFaceAnchor) {
                    mutableTrackingData.tryEmit(
                        FaceTrackingData.notTracking(monotonicNowNs() / NANOS_PER_MILLISECOND),
                    )
                    break
                }
            }
        }

        @ObjCSignatureOverride
        override fun session(session: ARSession, didFailWithError: NSError) {
            setError(didFailWithError.localizedDescription)
        }
    }
}
