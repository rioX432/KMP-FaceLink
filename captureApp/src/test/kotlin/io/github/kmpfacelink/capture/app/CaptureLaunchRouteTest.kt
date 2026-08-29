package io.github.kmpfacelink.capture.app

import org.junit.Test
import kotlin.test.assertEquals

class CaptureLaunchRouteTest {
    @Test
    fun explicitPreviewCheckRouteCannotFallThroughToTheCaptureScreen() {
        assertEquals(CaptureLaunchRoute.PREVIEW_CHECK, captureLaunchRoute(previewCheckRequested = true))
        assertEquals(CaptureLaunchRoute.CAPTURE, captureLaunchRoute(previewCheckRequested = false))
    }
}
