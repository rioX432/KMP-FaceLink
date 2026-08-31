package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.BodyTracker
import io.github.kmpfacelink.api.HolisticTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackerOperationStatus
import io.github.kmpfacelink.model.TrackingModality
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import platform.ARKit.ARSession
import platform.AVFoundation.AVCaptureSession
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * iOS implementation of [HolisticTracker] that composes:
 * - ARKit face tracker (front-camera ARSession, independent)
 * - Vision hand + body trackers sharing one [SharedVisionCaptureSession] (RGB camera)
 *
 * ARKit and AVCaptureSession use different camera sensors, so they can run simultaneously.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class CompositeHolisticTracker(
    private val config: HolisticTrackerConfig,
) : HolisticTracker {

    // Hand + Body share a single AVCaptureSession if both are enabled
    private val sharedVisionSession: SharedVisionCaptureSession? =
        if (config.enableHand || config.enableBody) {
            SharedVisionCaptureSession(config.cameraFacing)
        } else {
            null
        }

    private val faceTracker = if (config.enableFace) {
        ARKitFaceTracker(
            config.faceConfig.copy(
                powerConfig = config.faceConfig.powerConfig.copy(
                    thermalPolicy = config.thermalPolicy,
                ),
            ),
        )
    } else {
        null
    }

    private val handTracker = if (config.enableHand) {
        VisionHandTracker(
            config.handConfig.copy(
                cameraFacing = config.cameraFacing,
                thermalPolicy = config.thermalPolicy,
            ),
            sharedVisionSession = sharedVisionSession,
        )
    } else {
        null
    }

    private val bodyTracker = if (config.enableBody) {
        createBodyTrackerInternal(config, sharedVisionSession)
    } else {
        null
    }

    private val armFusionProcessor: ArmFusionProcessor? =
        if (config.fuseBodyAndHandArm) ArmFusionProcessor() else null

    override val enabledModalities: Set<TrackingModality> = buildSet {
        if (config.enableFace) add(TrackingModality.FACE)
        if (config.enableHand) add(TrackingModality.HAND)
        if (config.enableBody) add(TrackingModality.BODY)
    }

    override val trackingData: Flow<HolisticTrackingData> = createCombinedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val pipelineLock = PlatformLock()
    private val released = AtomicInt(0)

    private var currentCameraFacing: CameraFacing = config.cameraFacing

    /**
     * The underlying ARSession from the face tracker, for camera preview.
     * Only available after [start] when face tracking is enabled.
     */
    val arSession: ARSession?
        get() = faceTracker?.arSession

    /**
     * The underlying AVCaptureSession from the shared Vision session, for camera preview.
     * Only available after [start] when hand or body tracking is enabled.
     */
    val visionCaptureSession: AVCaptureSession?
        get() = sharedVisionSession?.captureSession

    override suspend fun start() {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot start a released tracker" }
            if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return
            _state.value = TrackingState.STARTING
        }

        try {
            // Start each tracker individually; failures degrade gracefully
            val failures = startSubTrackers()

            if (failures.size == enabledModalities.size) {
                // All enabled trackers failed — cannot proceed
                val message = buildAllFailedMessage(failures)
                FaceLinkLogger.e(TAG, message)
                _errorMessage.value = message
                _state.value = TrackingState.ERROR
                return
            }

            logPartialFailures(failures)

            // Start the shared Vision capture session (feeds frames to hand + body)
            sharedVisionSession?.start()

            _state.value = TrackingState.TRACKING
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Vision capture session or other unexpected failure
            FaceLinkLogger.e(TAG, "Failed to start holistic tracking", e)
            _errorMessage.value = e.message ?: e.toString()
            _state.value = TrackingState.ERROR
            throw e
        }
    }

    /**
     * Attempts to start each enabled sub-tracker, catching individual failures.
     * Returns a list of (modality, exception) pairs for trackers that failed.
     */
    private suspend fun startSubTrackers(): List<Pair<TrackingModality, Exception>> {
        val failures = mutableListOf<Pair<TrackingModality, Exception>>()
        faceTracker?.let {
            tryStartTracker(TrackingModality.FACE) { it.start() }?.let { failures.add(it) }
        }
        handTracker?.let {
            tryStartTracker(TrackingModality.HAND) { it.start() }?.let { failures.add(it) }
        }
        bodyTracker?.let {
            tryStartTracker(TrackingModality.BODY) { it.start() }?.let { failures.add(it) }
        }
        return failures
    }

    private suspend fun tryStartTracker(
        modality: TrackingModality,
        startAction: suspend () -> Unit,
    ): Pair<TrackingModality, Exception>? {
        return try {
            startAction()
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            FaceLinkLogger.e(TAG, "$modality tracker failed to start", e)
            modality to e
        }
    }

    private fun buildAllFailedMessage(
        failures: List<Pair<TrackingModality, Exception>>,
    ): String {
        val details = failures.joinToString("; ") { (mod, ex) ->
            "$mod: ${ex.message ?: ex.toString()}"
        }
        return "All enabled trackers failed to start: $details"
    }

    private fun logPartialFailures(
        failures: List<Pair<TrackingModality, Exception>>,
    ) {
        for ((modality, exception) in failures) {
            FaceLinkLogger.w(
                TAG,
                "$modality tracker disabled: ${exception.message ?: exception.toString()}",
            )
        }
    }

    override suspend fun stop() {
        pipelineLock.withLock {
            sharedVisionSession?.stop()
            faceTracker?.stop()
            handTracker?.stop()
            bodyTracker?.stop()
            _state.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        released.store(1)
        pipelineLock.withLock {
            sharedVisionSession?.release()
            faceTracker?.release()
            handTracker?.release()
            bodyTracker?.release()
            _state.value = TrackingState.RELEASED
        }
    }

    override fun updateSmoothing(config: SmoothingConfig): TrackerOperationStatus {
        return pipelineLock.withLock {
            check(released.load() == 0) { "Cannot update smoothing on a released tracker" }
            val faceTrackerStatus = faceTracker?.updateSmoothing(config)
            val handTrackerStatus = handTracker?.updateSmoothing(config)
            val bodyTrackerStatus = bodyTracker?.updateSmoothing(config)
            aggregate(faceTrackerStatus, handTrackerStatus, bodyTrackerStatus)
        }
    }

    override fun resetSmoothing(): TrackerOperationStatus {
        return pipelineLock.withLock {
            check(released.load() == 0) { "Cannot reset smoothing on a released tracker" }
            val faceTrackerStatus = faceTracker?.resetSmoothing()
            val handTrackerStatus = handTracker?.resetSmoothing()
            val bodyTrackerStatus = bodyTracker?.resetSmoothing()
            aggregate(faceTrackerStatus, handTrackerStatus, bodyTrackerStatus)
        }
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot switch camera on a released tracker" }
            if (currentCameraFacing == facing) return
            // ARKit face tracking only supports front camera
            if (config.enableFace && facing == CameraFacing.BACK) {
                throw UnsupportedOperationException(
                    "Cannot switch to back camera: this ARKit face tracker uses the front camera",
                )
            }
            val wasTracking = _state.value == TrackingState.TRACKING
            currentCameraFacing = facing
            if (wasTracking) {
                sharedVisionSession?.switchCamera(facing)
            }
        }
    }

    private fun createBodyTrackerInternal(
        config: HolisticTrackerConfig,
        sharedSession: SharedVisionCaptureSession?,
    ): BodyTracker {
        val bodyConfig = config.bodyConfig.copy(
            cameraFacing = config.cameraFacing,
            thermalPolicy = config.thermalPolicy,
        )
        return if (bodyConfig.enable3D) {
            VisionBodyTracker3D(bodyConfig, sharedVisionSession = sharedSession)
        } else {
            VisionBodyTracker(bodyConfig, sharedVisionSession = sharedSession)
        }
    }

    private fun createCombinedFlow(): Flow<HolisticTrackingData> {
        val faceFlow: Flow<FaceTrackingData?> = faceTracker?.trackingData ?: flowOf(null)
        val handFlow: Flow<HandTrackingData?> = handTracker?.trackingData ?: flowOf(null)
        val bodyFlow: Flow<BodyTrackingData?> = bodyTracker?.trackingData ?: flowOf(null)

        return combine(faceFlow, handFlow, bodyFlow) { face, hand, body ->
            buildHolisticData(face, hand, body)
        }
    }

    private fun buildHolisticData(
        face: FaceTrackingData?,
        hand: HandTrackingData?,
        body: BodyTrackingData?,
    ): HolisticTrackingData {
        val timestampMs = maxOf(
            face?.timestampMs ?: 0L,
            hand?.timestampMs ?: 0L,
            body?.timestampMs ?: 0L,
        )
        // Use the most severe thermal state from any sub-tracker
        val thermalState = maxOf(
            face?.thermalState?.ordinal ?: 0,
            hand?.thermalState?.ordinal ?: 0,
            body?.thermalState?.ordinal ?: 0,
        ).let { io.github.kmpfacelink.model.ThermalState.entries[it] }

        return HolisticTrackingData(
            face = face,
            hand = hand,
            body = body,
            arm = armFusionProcessor?.process(body, hand),
            timestampMs = timestampMs,
            activeModalities = enabledModalities,
            thermalState = thermalState,
        )
    }

    companion object {
        private const val TAG = "CompositeHolisticTracker"
    }

    /**
     * The status for an operation forwarded to the sub-trackers.
     *
     * A composite reports [TrackerOperationStatus.APPLIED] only when every tracker it actually
     * holds applied the operation. Reporting success while one sub-tracker silently ignored the
     * call would hide exactly the gap this status exists to surface. Absent sub-trackers are not
     * counted: the composite was not built with them.
     */
    private fun aggregate(vararg statuses: TrackerOperationStatus?): TrackerOperationStatus {
        val present = statuses.filterNotNull()
        return if (present.isNotEmpty() && present.all { it == TrackerOperationStatus.APPLIED }) {
            TrackerOperationStatus.APPLIED
        } else {
            TrackerOperationStatus.UNSUPPORTED
        }
    }
}
