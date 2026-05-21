import SwiftUI

/// ARKit-MediaPipe calibration capture tool.
///
/// Records ARKit face blendshape ground truth and front-camera RGB frames
/// simultaneously, producing paired datasets for parameter calibration.
@main
struct CaptureAppApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
