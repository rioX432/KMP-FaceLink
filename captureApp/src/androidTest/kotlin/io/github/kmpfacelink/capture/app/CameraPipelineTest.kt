package io.github.kmpfacelink.capture.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whether this phone's camera actually reaches inference.
 *
 * Not checkable anywhere but on a device: the analyzer thread, the delegate MediaPipe ends up
 * resolving and the resolution CameraX settles on are all properties of the hardware. A face is
 * neither needed nor expected — a result that reports no face still proves the whole path ran, which
 * is what has to hold before a take is worth attempting.
 */
@RunWith(AndroidJUnit4::class)
class CameraPipelineTest {

    @Before
    fun grantCamera() {
        // Granted through the shell rather than with a rule, so this needs no extra test artifact.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant $PACKAGE android.permission.CAMERA")
            .close()
        Thread.sleep(SETTLE_MS)
    }

    @Test
    fun inferenceResultsArriveFromTheCamera() {
        ActivityScenario.launch(CaptureActivity::class.java).use { scenario ->
            var observed = 0
            var gate = "never sampled"
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (System.currentTimeMillis() < deadline && observed == 0) {
                scenario.onActivity { activity ->
                    activity.captureSession?.uiState?.value?.let { state ->
                        observed = state.resultsObserved
                        gate = state.gate.toString()
                    }
                }
                Thread.sleep(POLL_MS)
            }
            assertTrue(
                "no inference result arrived within $TIMEOUT_MS ms; last gate=$gate",
                observed > 0,
            )
        }
    }

    private companion object {
        const val PACKAGE = "io.github.kmpfacelink.capture.app"
        const val TIMEOUT_MS = 20_000L
        const val POLL_MS = 250L
        const val SETTLE_MS = 300L
    }
}
