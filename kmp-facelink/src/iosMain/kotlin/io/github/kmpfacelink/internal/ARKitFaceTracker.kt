@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedFace
import io.github.kmpfacelink.model.filterByActive
import io.github.kmpfacelink.util.BlendShapeInterpolator
import io.github.kmpfacelink.util.BlendShapeMapPool
import io.github.kmpfacelink.util.BlendShapeSmoother
import io.github.kmpfacelink.util.Calibrator
import io.github.kmpfacelink.util.ConfidenceFrameStrategy
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import platform.ARKit.ARFaceAnchor
import platform.ARKit.ARFaceTrackingConfiguration
import platform.ARKit.ARSession
import platform.ARKit.ARSessionDelegateProtocol
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class ARKitFaceTracker(
    private val config: FaceTrackerConfig,
) : BaseTracker<FaceTrackingData>(
    thermalMonitor = IosThermalMonitor(),
    frameThrottler = FrameThrottler(config.thermalPolicy),
),
    FaceTracker {

    override val trackingData: Flow<FaceTrackingData> = super.trackingData
    override val state: StateFlow<TrackingState> = super.state
    override val errorMessage: StateFlow<String?> = super.errorMessage

    private var smoother: BlendShapeSmoother? = config.createSmoother()
    private val calibrator = Calibrator(
        warmupFrames = config.calibrationConfig.warmupFrames,
        decayRate = config.calibrationConfig.rangeDecayRate,
    )

    private val batteryMonitor = IosBatteryMonitor()
    private val powerModeManager: PowerModeManager? = config.powerMode?.let { mode ->
        PowerModeManager(
            powerMode = mode,
            thermalMonitor = thermalMonitor,
            batteryMonitor = batteryMonitor,
            lowBatteryThreshold = config.lowBatteryThreshold,
            enableIdleDetection = config.enableIdleDetection,
        )
    }

    private val frameSkipStrategy = ConfidenceFrameStrategy(config.frameSkipConfig)
    private val interpolator = BlendShapeInterpolator()
    private var lastConfidence: Float = 0f

    // Pre-allocated scratch map for mapARKitBlendShapes — reused each frame.
    // Callers that store the result must copy via HashMap(result).
    private val blendShapePool = BlendShapeMapPool()
    private val blendShapeScratch: HashMap<BlendShape, Float> = blendShapePool.create()

    // Pre-allocated FloatArray for the 4x4 transform matrix — reused each frame.
    private val transformMatrix = FloatArray(16)

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
        session.runWithConfiguration(configuration)

        pipelineLock.withLock { transitionToTracking() }
    }

    override suspend fun stop() {
        pipelineLock.withLock {
            arSession?.pause()
            mutableState.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        pipelineLock.withLock {
            released.store(1)
            arSession?.pause()
            arSession?.delegate = null
            arSession = null
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
            smoother?.reset()
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            checkNotReleased("update smoothing")
            smoother = config.createFaceSmoother()
        }
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        pipelineLock.withLock { checkNotReleased("switch camera") }
        if (facing == CameraFacing.BACK) {
            throw UnsupportedOperationException(
                "ARKit face tracking requires the front-facing TrueDepth camera",
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

    private fun processFaceAnchor(faceAnchor: ARFaceAnchor) {
        val timestampMs = (arSession?.currentFrame?.timestamp?.times(1000))?.toLong()
            ?: currentTimeMillis()

        // Frame throttling: use PowerModeManager when available, else legacy FrameThrottler
        if (!shouldProcessFrame(timestampMs)) return

        // Confidence-guided skip: emit interpolated data for stable frames
        val decision = frameSkipStrategy.evaluate(lastConfidence)
        if (decision == FrameDecision.SKIP) {
            emitInterpolatedFrame(timestampMs)
            return
        }

        // Full processing path
        val faceConfidence = if (faceAnchor.isTracked) 1.0f else 0.0f
        processFullFrame(faceAnchor, timestampMs, faceConfidence)
    }

    private fun processFullFrame(faceAnchor: ARFaceAnchor, timestampMs: Long, faceConfidence: Float) {
        @Suppress("UNCHECKED_CAST")
        val rawBlendShapes = faceAnchor.blendShapes as? Map<String, NSNumber> ?: emptyMap()
        var blendShapes: BlendShapeData = mapARKitBlendShapes(rawBlendShapes)

        val processedBlendShapes = pipelineLock.withLock {
            if (isReleased()) return

            if (config.calibrationConfig.enabled) {
                blendShapes = calibrator.calibrate(blendShapes)
            }
            smoother?.let { blendShapes = it.smooth(blendShapes, timestampMs) }
            powerModeManager?.onFrameProcessed(blendShapes, timestampMs)
            HashMap(blendShapes).filterByActive(config.activeBlendShapes)
        }

        val headTransform = extractTransform(faceAnchor)
        val gazeData = GazeEstimator.estimate(processedBlendShapes)

        // Update interpolator and strategy with processed data
        lastConfidence = faceConfidence
        interpolator.onFrameProcessed(processedBlendShapes, headTransform, timestampMs)
        frameSkipStrategy.onFrameProcessed(processedBlendShapes, faceConfidence)

        val trackedFace = TrackedFace(
            faceIndex = 0,
            blendShapes = processedBlendShapes,
            headTransform = headTransform,
            gazeData = gazeData,
            confidence = faceConfidence,
        )

        val data = FaceTrackingData(
            blendShapes = processedBlendShapes,
            headTransform = headTransform,
            gazeData = gazeData,
            timestampMs = timestampMs,
            isTracking = true,
            faces = listOf(trackedFace),
            thermalState = thermalMonitor.thermalState.value,
        )

        mutableTrackingData.tryEmit(data)
    }

    private fun emitInterpolatedFrame(timestampMs: Long) {
        val blendShapes = interpolator.interpolateBlendShapes(timestampMs) ?: return
        val headTransform = interpolator.interpolateTransform(timestampMs) ?: return
        val filtered = blendShapes.filterByActive(config.activeBlendShapes)

        val data = FaceTrackingData(
            blendShapes = filtered,
            headTransform = headTransform,
            gazeData = GazeEstimator.estimate(filtered),
            timestampMs = timestampMs,
            isTracking = true,
            faces = listOf(
                TrackedFace(
                    faceIndex = 0,
                    blendShapes = filtered,
                    headTransform = headTransform,
                    gazeData = GazeEstimator.estimate(filtered),
                    confidence = lastConfidence,
                ),
            ),
            thermalState = thermalMonitor.thermalState.value,
        )
        mutableTrackingData.tryEmit(data)
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

    private inner class FaceTrackingDelegate : NSObject(), ARSessionDelegateProtocol {
        @ObjCSignatureOverride
        override fun session(session: ARSession, didUpdateAnchors: List<*>) {
            for (anchor in didUpdateAnchors) {
                if (anchor is ARFaceAnchor) {
                    processFaceAnchor(anchor)
                    break // Only process first face
                }
            }
        }

        @ObjCSignatureOverride
        override fun session(session: ARSession, didRemoveAnchors: List<*>) {
            for (anchor in didRemoveAnchors) {
                if (anchor is ARFaceAnchor) {
                    mutableTrackingData.tryEmit(FaceTrackingData.notTracking(currentTimeMillis()))
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
