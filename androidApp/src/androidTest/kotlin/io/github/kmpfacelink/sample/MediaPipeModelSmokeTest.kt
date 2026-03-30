package io.github.kmpfacelink.sample

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests that verify MediaPipe model files can be loaded on real devices.
 * These catch packaging or asset-bundling issues that unit tests cannot detect.
 */
@RunWith(AndroidJUnit4::class)
class MediaPipeModelSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun faceLandmarkerModelLoads() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("models/face_landmarker_v2_with_blendshapes.task")
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumFaces(1)
            .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)
            .build()

        val landmarker = FaceLandmarker.createFromOptions(context, options)
        assertNotNull("FaceLandmarker should be created successfully", landmarker)
        landmarker.close()
    }

    @Test
    fun handLandmarkerModelLoads() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("models/hand_landmarker.task")
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(1)
            .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)
            .build()

        val landmarker = HandLandmarker.createFromOptions(context, options)
        assertNotNull("HandLandmarker should be created successfully", landmarker)
        landmarker.close()
    }
}
