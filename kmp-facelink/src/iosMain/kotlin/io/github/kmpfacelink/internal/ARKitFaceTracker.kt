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
import io.github.kmpfacelink.util.BlendShapeMapPool
import io.github.kmpfacelink.util.BlendShapeSmoother
import io.github.kmpfacelink.util.Calibrator
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.GazeEstimator
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.ThermalMonitor
import io.github.kmpfacelink.util.TransformUtils
import io.github.kmpfacelink.util.createSmoother
import io.github.kmpfacelink.util.withLock
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.get
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.ARKit.ARFaceAnchor
import platform.ARKit.ARFaceTrackingConfiguration
import platform.ARKit.ARSession
import platform.ARKit.ARSessionDelegateProtocol
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class ARKitFaceTracker(
    private val config: FaceTrackerConfig,
) : FaceTracker {

    private val _trackingData = MutableSharedFlow<FaceTrackingData>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    override val trackingData: Flow<FaceTrackingData> = _trackingData.asSharedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val pipelineLock = PlatformLock()
    private val released = AtomicInt(0)

    private var smoother: BlendShapeSmoother? = config.smoothingConfig.createSmoother()
    private val calibrator = Calibrator(
        warmupFrames = config.calibrationConfig.warmupFrames,
        decayRate = config.calibrationConfig.rangeDecayRate,
    )

    private val thermalMonitor: ThermalMonitor = IosThermalMonitor()
    private val frameThrottler = FrameThrottler(config.thermalPolicy)

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
            check(released.load() == 0) { "Cannot start a released tracker" }
            if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return
            _state.value = TrackingState.STARTING
        }

        if (!ARFaceTrackingConfiguration.isSupported) {
            _errorMessage.value = "ARFaceTracking is not supported on this device"
            _state.value = TrackingState.ERROR
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

        pipelineLock.withLock {
            if (released.load() == 0) {
                _state.value = TrackingState.TRACKING
            }
        }
    }

    override suspend fun stop() {
        pipelineLock.withLock {
            arSession?.pause()
            _state.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        pipelineLock.withLock {
            released.store(1)
            arSession?.pause()
            arSession?.delegate = null
            arSession = null
            thermalMonitor.release()
            _state.value = TrackingState.RELEASED
        }
    }

    override fun resetCalibration() {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot reset calibration on a released tracker" }
            calibrator.reset()
            smoother?.reset()
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot update smoothing on a released tracker" }
            smoother = config.createSmoother()
        }
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot switch camera on a released tracker" }
        }
        if (facing == CameraFacing.BACK) {
            throw UnsupportedOperationException(
                "ARKit face tracking requires the front-facing TrueDepth camera",
            )
        }
        // Already using front camera — no-op
    }

    private fun processFaceAnchor(faceAnchor: ARFaceAnchor) {
        val timestampMs = (arSession?.currentFrame?.timestamp?.times(1000))?.toLong()
            ?: currentTimeMillis()

        // Thermal throttling: skip frame if needed
        val currentThermalState = thermalMonitor.thermalState.value
        if (!frameThrottler.shouldProcess(timestampMs, currentThermalState)) return

        // Extract blend shapes
        @Suppress("UNCHECKED_CAST")
        val rawBlendShapes = faceAnchor.blendShapes as? Map<String, NSNumber> ?: emptyMap()
        var blendShapes: BlendShapeData = mapARKitBlendShapes(rawBlendShapes)

        // Pipeline: calibrate → smooth (protected by lock)
        val processedBlendShapes = pipelineLock.withLock {
            if (released.load() != 0) return

            // Apply calibration
            if (config.calibrationConfig.enabled) {
                blendShapes = calibrator.calibrate(blendShapes)
            }

            // Apply smoothing
            smoother?.let { blendShapes = it.smooth(blendShapes, timestampMs) }

            // Defensive copy: pipeline stages return reusable scratch maps.
            // The result is stored in FaceTrackingData (held by the SharedFlow replay buffer),
            // so we must snapshot it before the next frame overwrites the scratch map.
            HashMap(blendShapes)
        }

        // Extract head transform from simd_float4x4
        val headTransform = extractTransform(faceAnchor)

        val gazeData = GazeEstimator.estimate(processedBlendShapes)

        val faceConfidence = if (faceAnchor.isTracked) 1.0f else 0.0f

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
            thermalState = currentThermalState,
        )

        _trackingData.tryEmit(data)
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
                    _trackingData.tryEmit(FaceTrackingData.notTracking(currentTimeMillis()))
                    break
                }
            }
        }

        @ObjCSignatureOverride
        override fun session(session: ARSession, didFailWithError: NSError) {
            _errorMessage.value = didFailWithError.localizedDescription
            _state.value = TrackingState.ERROR
        }
    }
}
