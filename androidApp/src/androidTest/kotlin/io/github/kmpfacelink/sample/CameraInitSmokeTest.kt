package io.github.kmpfacelink.sample

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests that verify CameraX initialization on real devices.
 * These detect hardware-specific camera issues that emulators miss.
 */
@RunWith(AndroidJUnit4::class)
class CameraInitSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun cameraProviderIsAvailable() {
        val future = ProcessCameraProvider.getInstance(context)
        val provider = future.get(10, TimeUnit.SECONDS)
        assertNotNull("ProcessCameraProvider should be available", provider)
    }

    @Test
    fun frontCameraIsAvailable() {
        assumeTrue(
            "Device must have a front camera",
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
        )
        val future = ProcessCameraProvider.getInstance(context)
        val provider = future.get(10, TimeUnit.SECONDS)
        val hasCamera = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        assertTrue("Front camera should be available", hasCamera)
    }
}
