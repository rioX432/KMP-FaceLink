package io.github.kmpfacelink.capture.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kmpfacelink.capture.CaptureSession

/**
 * Displays the platform camera stream owned by [session]. iOS may render ARKit's native face mesh
 * here, while the common 478-point landmark overlay remains absent because the topologies differ.
 *
 * The preview is render-only and shares the recorder's existing camera/AR session. It must never
 * open a second camera, persist a frame, or change the pixels submitted to inference and storage.
 * The adapter must apply [contentScale] as a centered aspect-ratio operation so a normalized common
 * overlay can use the same mapping. This is a source contract, not evidence of physical alignment;
 * orientation, mirroring, camera crop, and native-view behavior remain device controls.
 */
@Composable
internal expect fun CaptureCameraPreview(
    session: CaptureSession,
    contentScale: PreviewContentScale,
    modifier: Modifier,
)

/**
 * Guided capture fills the participant viewport. One common value is passed to both native preview
 * and normalized overlay so the layers cannot silently choose FIT and FILL independently.
 */
internal val GUIDED_CAPTURE_PREVIEW_CONTENT_SCALE: PreviewContentScale = PreviewContentScale.FILL
