package io.github.kmpfacelink.sample

import io.github.kmpfacelink.api.CheekPuffContourObservation
import io.github.kmpfacelink.api.CheekPuffContourObservationSession
import io.github.kmpfacelink.model.FaceTrackingData

private const val GEOMETRY_SETTLING_MS = 1_500L

internal sealed interface GeometryStage {
    data object NeedsNeutral : GeometryStage
    data class Settling(val startedAtMs: Long) : GeometryStage
    data class Collecting(val frames: Int) : GeometryStage
    data object Ready : GeometryStage
}

internal data class GeometryFrameUpdate(
    val stage: GeometryStage,
    val observation: CheekPuffContourObservation? = null,
)

internal fun advanceGeometryEvidence(
    data: FaceTrackingData,
    session: CheekPuffContourObservationSession?,
    stage: GeometryStage,
): GeometryFrameUpdate {
    val landmarks = data.landmarks?.takeIf {
        it.size == CheekPuffContourObservationSession.LANDMARK_COUNT
    } ?: return GeometryFrameUpdate(stage)
    session ?: return GeometryFrameUpdate(stage)

    return when (stage) {
        GeometryStage.NeedsNeutral -> GeometryFrameUpdate(stage)
        is GeometryStage.Settling -> {
            val nextStage = if (data.timestampMs - stage.startedAtMs >= GEOMETRY_SETTLING_MS) {
                GeometryStage.Collecting(frames = 0)
            } else {
                stage
            }
            GeometryFrameUpdate(nextStage)
        }
        is GeometryStage.Collecting -> {
            session.addNeutralFrame(landmarks, data.timestampMs)
            val count = stage.frames + 1
            val nextStage = if (count >= CheekPuffContourObservationSession.RECOMMENDED_NEUTRAL_FRAMES) {
                session.finishNeutralCalibration()
                GeometryStage.Ready
            } else {
                GeometryStage.Collecting(count)
            }
            GeometryFrameUpdate(nextStage)
        }
        GeometryStage.Ready -> GeometryFrameUpdate(stage, session.observe(landmarks, data.timestampMs))
    }
}

internal fun createGeometrySession(data: FaceTrackingData?): CheekPuffContourObservationSession? {
    data ?: return null
    if (!data.isTracking) return null
    if (data.landmarks?.size != CheekPuffContourObservationSession.LANDMARK_COUNT) return null
    if (data.sourceImageWidth <= 0 || data.sourceImageHeight <= 0) return null
    return CheekPuffContourObservationSession(
        imageWidth = data.sourceImageWidth,
        imageHeight = data.sourceImageHeight,
    )
}
