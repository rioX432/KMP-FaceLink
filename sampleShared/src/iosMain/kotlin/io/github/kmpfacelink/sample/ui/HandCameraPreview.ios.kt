@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("DEPRECATION")

package io.github.kmpfacelink.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.getCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

/**
 * iOS camera preview using AVCaptureSession from the hand tracker.
 * Displays the camera feed via AVCaptureVideoPreviewLayer.
 */
@Composable
public actual fun HandCameraPreview(
    handTracker: HandTracker,
    modifier: Modifier,
) {
    var previewLayer by remember { mutableStateOf<AVCaptureVideoPreviewLayer?>(null) }

    UIKitView(
        factory = {
            UIView().apply {
                backgroundColor = platform.UIKit.UIColor.blackColor
            }
        },
        modifier = modifier,
        update = { view ->
            val session = handTracker.getCaptureSession()
            if (session != null && previewLayer == null) {
                val layer = AVCaptureVideoPreviewLayer(session = session)
                layer.videoGravity = AVLayerVideoGravityResizeAspectFill
                layer.frame = view.bounds
                view.layer.addSublayer(layer)
                previewLayer = layer
            }
            previewLayer?.let { layer ->
                CATransaction.begin()
                CATransaction.setValue(true, kCATransactionDisableActions)
                layer.frame = view.bounds
                CATransaction.commit()
            }
        },
    )
}
