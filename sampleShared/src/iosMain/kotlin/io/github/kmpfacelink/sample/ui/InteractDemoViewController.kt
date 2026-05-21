package io.github.kmpfacelink.sample.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Creates a UIViewController hosting the shared [InteractDemoScreen] via Compose Multiplatform.
 *
 * Use from Swift:
 * ```swift
 * let vc = InteractDemoViewControllerKt.InteractDemoViewController()
 * ```
 */
@Suppress("FunctionName")
public fun InteractDemoViewController(): UIViewController = ComposeUIViewController {
    InteractDemoScreen()
}
