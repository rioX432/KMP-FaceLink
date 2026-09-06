package io.github.kmpfacelink

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.TrackedFace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Null and empty are different answers about landmarks, and this pins the difference.
 *
 * Before landmarks were nullable, an estimator that produces none at all — ARKit, which exposes
 * face-local geometry under a different contract — was indistinguishable from one that ran and
 * found nothing. Both arrived as an empty list, so a consumer could not tell "ask a different
 * estimator" from "wait for the next frame".
 */
class FaceLandmarkAvailabilityTest {

    private fun frame(landmarks: List<FaceLandmark>?) = FaceTrackingData(
        blendShapes = emptyMap(),
        headTransform = HeadTransform(),
        landmarks = landmarks,
        timestampMs = 0L,
        isTracking = true,
    )

    @Test
    fun anEstimatorWithNoLandmarksToGiveReportsNull() {
        assertNull(frame(null).landmarks)
    }

    @Test
    fun anEstimatorThatLookedAndFoundNoneReportsAnEmptyList() {
        val landmarks = frame(emptyList()).landmarks
        assertTrue(landmarks != null && landmarks.isEmpty())
    }

    @Test
    fun theTwoAnswersAreNotEqual() {
        assertTrue(frame(null).landmarks != frame(emptyList()).landmarks)
    }

    @Test
    fun defaultingToNullIsWhatMakesAnUnsetListMeanUnavailable() {
        // ARKit never assigns this property, so the default is the answer its consumers get.
        val face = TrackedFace(
            faceIndex = 0,
            blendShapes = emptyMap(),
            headTransform = HeadTransform(),
        )
        assertNull(face.landmarks)
    }

    @Test
    fun aPopulatedListSurvivesUnchanged() {
        val points = listOf(FaceLandmark(x = 0.1f, y = 0.2f))
        assertEquals(points, frame(points).landmarks)
    }
}
