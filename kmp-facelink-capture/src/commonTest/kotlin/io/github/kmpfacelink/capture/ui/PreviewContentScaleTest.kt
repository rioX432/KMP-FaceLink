package io.github.kmpfacelink.capture.ui

import io.github.kmpfacelink.capture.CaptureUiState
import io.github.kmpfacelink.capture.HardGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewContentScaleTest {

    @Test
    fun fitLetterboxesWithoutStretching() {
        val viewport = centeredPreviewViewport(
            sourceHeightOverWidth = 4f / 3f,
            targetWidth = 100f,
            targetHeight = 100f,
            contentScale = PreviewContentScale.FIT,
        )

        assertClose(75f, viewport.drawWidth)
        assertClose(100f, viewport.drawHeight)
        assertClose(12.5f, viewport.offsetX)
        assertClose(0f, viewport.offsetY)
        assertClose(50f, viewport.mapX(0.5f))
        assertClose(50f, viewport.mapY(0.5f))
    }

    @Test
    fun fillCenterCropsLandscapeInputInPortraitView() {
        val viewport = centeredPreviewViewport(
            sourceHeightOverWidth = 3f / 4f,
            targetWidth = 300f,
            targetHeight = 600f,
            contentScale = PreviewContentScale.FILL,
        )

        assertClose(800f, viewport.drawWidth)
        assertClose(600f, viewport.drawHeight)
        assertClose(-250f, viewport.offsetX)
        assertClose(0f, viewport.offsetY)
        assertClose(150f, viewport.mapX(0.5f))
        assertClose(300f, viewport.mapY(0.5f))
    }

    @Test
    fun fitLetterboxesLandscapeInputInPortraitView() {
        val viewport = centeredPreviewViewport(
            sourceHeightOverWidth = 3f / 4f,
            targetWidth = 300f,
            targetHeight = 600f,
            contentScale = PreviewContentScale.FIT,
        )

        assertClose(300f, viewport.drawWidth)
        assertClose(225f, viewport.drawHeight)
        assertClose(0f, viewport.offsetX)
        assertClose(187.5f, viewport.offsetY)
    }

    @Test
    fun guidedCapturePassesOneExplicitFillPolicyToBothLayers() {
        assertEquals(PreviewContentScale.FILL, GUIDED_CAPTURE_PREVIEW_CONTENT_SCALE)
    }

    @Test
    fun invalidAspectFallsBackToSquareAndEmptyTargetIsStable() {
        val square = centeredPreviewViewport(
            sourceHeightOverWidth = Float.NaN,
            targetWidth = 120f,
            targetHeight = 80f,
            contentScale = PreviewContentScale.FIT,
        )
        assertClose(80f, square.drawWidth)
        assertClose(80f, square.drawHeight)

        val empty = centeredPreviewViewport(
            sourceHeightOverWidth = 1f,
            targetWidth = 0f,
            targetHeight = 80f,
            contentScale = PreviewContentScale.FILL,
        )
        assertEquals(PreviewViewport(0f, 0f, 0f, 0f), empty)
    }

    @Test
    fun diagnosticsExposeOnlyModeInputAgeAndGateCategories() {
        val state = CaptureUiState(
            gate = HardGate.Result(
                hasFace = true,
                centered = false,
                sized = true,
                bright = true,
                distanced = false,
            ),
            resultsObserved = 2,
        )

        assertEquals(
            "確認のみ / 入力あり / age=3s / 顔あり / F1 C0 S1 B1 D0",
            gateDiagnosticSummary(state, CaptureSurfaceMode.PREVIEW_ONLY, 3),
        )
        assertEquals(
            "収録 / 入力あり / age=3s / 顔あり / F1 C0 S1 B1 D0",
            gateDiagnosticSummary(state, CaptureSurfaceMode.RECORDING_PROTOCOL, 3),
        )
        assertEquals(
            "確認のみ / 入力待ち / age=未観測 / 顔なし / F0 C0 S0 B0 D0",
            gateDiagnosticSummary(CaptureUiState(), CaptureSurfaceMode.PREVIEW_ONLY, null),
        )
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(
            kotlin.math.abs(expected - actual) < 0.001f,
            "expected $expected but was $actual",
        )
    }
}
