import SwiftUI
import UIKit

/// Generic SwiftUI wrapper for any UIViewController.
/// Used to embed Compose Multiplatform screens in the SwiftUI navigation.
struct ComposeViewControllerWrapper: UIViewControllerRepresentable {
    let viewControllerFactory: () -> UIViewController

    func makeUIViewController(context: Context) -> UIViewController {
        viewControllerFactory()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Compose manages its own state; no SwiftUI-driven updates needed
    }
}
