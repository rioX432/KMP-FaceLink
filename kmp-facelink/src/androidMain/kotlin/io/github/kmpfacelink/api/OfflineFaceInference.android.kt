package io.github.kmpfacelink.api

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.internal.FaceLandmarkerFactory
import io.github.kmpfacelink.internal.FaceLandmarkerResultMapper
import io.github.kmpfacelink.internal.FaceResultProcessor
import io.github.kmpfacelink.internal.buildAndroidTrackingEpoch
import io.github.kmpfacelink.internal.modelAssetSha256
import io.github.kmpfacelink.internal.newSecureSessionInstanceId
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.model.TrackingContextEpoch
import io.github.kmpfacelink.util.GazeEstimator
import io.github.kmpfacelink.util.PlatformLock
import java.nio.ByteBuffer

@OptIn(ExperimentalFaceLinkApi::class)
private class MediaPipeOfflineFaceInference(
    private val platformContext: PlatformContext,
    private val landmarker: FaceLandmarker,
    private val resolvedDelegate: com.google.mediapipe.tasks.core.Delegate,
    private val mode: InferenceRunningMode,
    private val config: FaceTrackerConfig,
) : OfflineFaceInference {

    // Reused across frames: allocating a 240x320 bitmap per frame would dominate the run.
    private var scratch: Bitmap? = null
    private var released: Boolean = false
    private var epoch: TrackingContextEpoch? = null
    private val pipelineLock = PlatformLock()
    private val resultProcessor = FaceResultProcessor(
        config = config,
        pipelineLock = pipelineLock,
        isReleased = { released },
        // Controlled offline replay has no adaptive thermal lane. The configured enhancer state
        // therefore remains fixed for the complete runner lifetime.
        isEnhancerDisabled = { false },
    )
    private val sessionInstanceId = newSecureSessionInstanceId()
    private val modelSha256 = modelAssetSha256(platformContext, config.modelAssetPath)

    override fun infer(frame: OfflineFrame): OfflineFaceResult {
        check(!released) { "offline inference is released" }
        return invokeEstimator(frame).result.toOfflineResult()
    }

    override fun inferMeasured(frame: OfflineFrame): OfflineMeasuredFaceResult {
        check(!released) { "offline inference is released" }
        val contextEpoch = epochFor(frame)
        val invocation = invokeEstimator(frame)

        val processed = invocation.result.toProcessedOutput(frame, contextEpoch)
        val processEndedNs = SystemClock.elapsedRealtimeNanos()

        // Raw export is deliberately materialized after the normal SDK output is ready, so its
        // array allocations cannot inflate the SDK processing segment.
        val rawStartedNs = SystemClock.elapsedRealtimeNanos()
        val raw = invocation.result.toOfflineResult()
        val rawEndedNs = SystemClock.elapsedRealtimeNanos()

        return OfflineMeasuredFaceResult(
            raw = raw,
            processed = processed,
            contextEpoch = contextEpoch,
            timing = OfflineFaceTiming(
                clock = OfflineFaceTimingClock.ANDROID_ELAPSED_REALTIME_NANOS,
                estimatorCallNs = invocation.estimatorEndedNs - invocation.estimatorStartedNs,
                sdkProcessToOutputReadyNs = processEndedNs - invocation.estimatorEndedNs,
                estimatorToSdkOutputReadyNs = processEndedNs - invocation.estimatorStartedNs,
                rawOutputMaterializationNs = rawEndedNs - rawStartedNs,
            ),
        )
    }

    private data class EstimatorInvocation(
        val result: FaceLandmarkerResult?,
        val estimatorStartedNs: Long,
        val estimatorEndedNs: Long,
    )

    private fun invokeEstimator(frame: OfflineFrame): EstimatorInvocation {
        val bitmap = scratch
            ?.takeIf { it.width == frame.width && it.height == frame.height }
            ?: createBitmap(frame.width, frame.height).also {
                scratch?.recycle()
                scratch = it
            }
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(frame.rgba))

        val image = BitmapImageBuilder(bitmap).build()
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val result: FaceLandmarkerResult? = when (mode) {
            InferenceRunningMode.IMAGE -> landmarker.detect(image)
            InferenceRunningMode.VIDEO -> landmarker.detectForVideo(image, frame.timestampMs)
            // Rejected at construction; unreachable.
            InferenceRunningMode.LIVE_STREAM -> null
        }
        val endedNs = SystemClock.elapsedRealtimeNanos()
        return EstimatorInvocation(result, startedNs, endedNs)
    }

    private fun epochFor(frame: OfflineFrame): TrackingContextEpoch {
        epoch?.let { existing ->
            require((existing.inputWidth as? io.github.kmpfacelink.model.DimensionValue.Known)?.value == frame.width) {
                "offline runner input width changed within one stateful run"
            }
            require(
                (existing.inputHeight as? io.github.kmpfacelink.model.DimensionValue.Known)?.value ==
                    frame.height,
            ) {
                "offline runner input height changed within one stateful run"
            }
            require(
                (existing.mirroredBeforeInference as? io.github.kmpfacelink.model.DimensionValue.Known)?.value ==
                    frame.mirroredBeforeInference,
            ) { "offline runner mirror condition changed within one stateful run" }
            return existing
        }
        return buildAndroidTrackingEpoch(
            epochId = 1u,
            sessionInstanceId = sessionInstanceId,
            config = config,
            resolvedDelegate = resolvedDelegate,
            modelSha256 = modelSha256,
            inputWidth = frame.width,
            inputHeight = frame.height,
            mirroredBeforeInference = frame.mirroredBeforeInference,
        ).also { epoch = it }
    }

    private fun FaceLandmarkerResult?.toProcessedOutput(
        frame: OfflineFrame,
        epoch: TrackingContextEpoch,
    ): FaceTrackingData? {
        val result = this ?: return null
        if (!result.faceBlendshapes().isPresent || result.faceBlendshapes().get().isEmpty()) return null

        val primaryLandmarks = FaceLandmarkerResultMapper.extractLandmarks(result, faceIndex = 0)
        val primaryBlendShapes = FaceLandmarkerResultMapper.extractBlendShapes(result, faceIndex = 0)
        val processedPrimary = resultProcessor.processPrimary(
            blendShapes = primaryBlendShapes,
            landmarks = primaryLandmarks,
            timestampMs = frame.timestampMs,
            epoch = epoch,
        ) ?: return null
        val primaryHeadTransform = FaceLandmarkerResultMapper.extractHeadTransform(result, faceIndex = 0)
        val faces = resultProcessor.buildTrackedFaces(
            result = result,
            processedPrimary = processedPrimary,
            primaryLandmarks = primaryLandmarks,
            primaryHeadTransform = primaryHeadTransform,
            timestampMs = frame.timestampMs,
            epoch = epoch,
        )
        return FaceTrackingData(
            blendShapes = processedPrimary.blendShapes,
            headTransform = primaryHeadTransform,
            gazeData = GazeEstimator.estimate(processedPrimary.blendShapes),
            landmarks = primaryLandmarks,
            sourceImageWidth = frame.width,
            sourceImageHeight = frame.height,
            timestampMs = frame.timestampMs,
            isTracking = true,
            faces = faces,
            contextEpoch = epoch,
        )
    }

    override fun release() {
        released = true
        landmarker.close()
        scratch?.recycle()
        scratch = null
    }
}

@OptIn(ExperimentalFaceLinkApi::class)
private fun FaceLandmarkerResult?.toOfflineResult(): OfflineFaceResult {
    val points = this?.faceLandmarks()?.firstOrNull()
        ?: return OfflineFaceResult(null, null, null)

    val landmarks = FloatArray(points.size * LANDMARK_COMPONENT_COUNT)
    points.forEachIndexed { i, p ->
        val offset = i * LANDMARK_COMPONENT_COUNT
        landmarks[offset] = p.x()
        landmarks[offset + Y_COMPONENT_OFFSET] = p.y()
        landmarks[offset + Z_COMPONENT_OFFSET] = p.z()
    }

    val blendshapes = faceBlendshapes().orElse(null)?.firstOrNull()?.let { categories ->
        FloatArray(categories.size) { categories[it].score() }
    }
    val transform = facialTransformationMatrixes().orElse(null)?.firstOrNull()

    return OfflineFaceResult(landmarks, blendshapes, transform)
}

@ExperimentalFaceLinkApi
public actual fun openOfflineFaceInference(
    platformContext: PlatformContext,
    config: FaceTrackerConfig,
): OfflineFaceInference {
    val mode = config.performanceConfig.runningMode
    require(mode != InferenceRunningMode.LIVE_STREAM) {
        "Offline inference needs IMAGE or VIDEO. LIVE_STREAM returns results through a callback " +
            "on its own schedule and drops frames to keep up, so it cannot answer what a given " +
            "frame produces."
    }
    // The result listener is never attached in these modes; the lambda is required by the
    // factory signature but MediaPipe drops it for IMAGE and VIDEO.
    val created = FaceLandmarkerFactory.create(platformContext, config) { _, _ -> }
    return MediaPipeOfflineFaceInference(
        platformContext = platformContext,
        landmarker = created.landmarker,
        resolvedDelegate = created.resolvedDelegate,
        mode = mode,
        config = config,
    )
}

private const val LANDMARK_COMPONENT_COUNT: Int = 3
private const val Y_COMPONENT_OFFSET: Int = 1
private const val Z_COMPONENT_OFFSET: Int = 2
