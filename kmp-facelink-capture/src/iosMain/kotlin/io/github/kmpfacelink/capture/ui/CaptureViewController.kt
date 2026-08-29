package io.github.kmpfacelink.capture.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.ComposeUIViewController
import io.github.kmpfacelink.capture.CaptureSession
import platform.UIKit.UIViewController

/** Hosts the shared recording screen in a native iOS application. */
@Suppress("FunctionName")
public fun CaptureViewController(session: CaptureSession): UIViewController = ComposeUIViewController {
    MaterialTheme {
        CaptureScreen(session)
    }
}

/** Hosts the no-record participant-preview check screen for a native iOS application. */
@Suppress("FunctionName")
public fun CapturePreviewCheckViewController(session: CaptureSession): UIViewController = ComposeUIViewController {
    MaterialTheme {
        CapturePreviewCheckScreen(session)
    }
}
