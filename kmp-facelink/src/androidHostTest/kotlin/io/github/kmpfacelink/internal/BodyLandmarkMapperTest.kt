package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.BodyJoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BodyLandmarkMapperTest {

    @Test
    fun mapJointWithValidIndex() {
        assertEquals(BodyJoint.NOSE, BodyLandmarkMapper.mapJoint(0))
        assertEquals(BodyJoint.LEFT_SHOULDER, BodyLandmarkMapper.mapJoint(11))
        assertEquals(BodyJoint.RIGHT_FOOT_INDEX, BodyLandmarkMapper.mapJoint(32))
    }

    @Test
    fun mapJointWithAllIndices() {
        for (joint in BodyJoint.entries) {
            assertEquals(joint, BodyLandmarkMapper.mapJoint(joint.index))
        }
    }

    @Test
    fun mapJointWithNegativeIndex() {
        assertNull(BodyLandmarkMapper.mapJoint(-1))
    }

    @Test
    fun mapJointWithIndexAboveRange() {
        assertNull(BodyLandmarkMapper.mapJoint(33))
        assertNull(BodyLandmarkMapper.mapJoint(100))
    }

    // mapLandmarks() depends on com.google.mediapipe.tasks.components.containers.NormalizedLandmark
    // which is an AAR class. Testing it requires Robolectric + MediaPipe on the test classpath.
    // Since NormalizedLandmark uses a builder pattern with Optional<Float> for visibility(),
    // it cannot be trivially constructed in unit tests. Skipping mapLandmarks tests here;
    // they should be covered by integration tests on a real device.
}
