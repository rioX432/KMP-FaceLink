import SwiftUI
import KMPFaceLink

struct ContentView: View {
    @StateObject private var viewModel = FaceTrackingViewModel()

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Status: \(viewModel.statusText)")
                .font(.headline)

            Text(viewModel.headRotationText)
                .font(.system(.body, design: .monospaced))

            Button(viewModel.isTracking ? "Stop Tracking" : "Start Tracking") {
                viewModel.toggleTracking()
            }
            .buttonStyle(.borderedProminent)

            ScrollView {
                Text(viewModel.blendShapesText)
                    .font(.system(.caption, design: .monospaced))
            }
        }
        .padding()
    }
}

@MainActor
class FaceTrackingViewModel: ObservableObject {
    @Published var statusText = "Idle"
    @Published var headRotationText = "Head: P=0.0 Y=0.0 R=0.0"
    @Published var blendShapesText = "Blend shapes will appear here..."
    @Published var isTracking = false

    private var tracker: FaceTracker?

    init() {
        let config = FaceTrackerConfig(
            enableSmoothing: true,
            smoothingFactor: 0.4,
            enableCalibration: false,
            cameraFacing: .front
        )
        tracker = FaceTrackerFactoryKt.createFaceTracker(
            platformContext: PlatformContext(),
            config: config
        )
    }

    func toggleTracking() {
        if isTracking {
            stopTracking()
        } else {
            startTracking()
        }
    }

    private func startTracking() {
        guard let tracker = tracker else { return }
        Task {
            try await tracker.start()
            isTracking = true
            statusText = "Tracking"
            observeData()
        }
    }

    private func stopTracking() {
        guard let tracker = tracker else { return }
        Task {
            try await tracker.stop()
            isTracking = false
            statusText = "Stopped"
        }
    }

    private func observeData() {
        // Note: Collecting Kotlin Flow from Swift requires
        // SKIE or a Flow wrapper. This is a simplified example.
        // In production, use SKIE or a custom CFlow wrapper.
    }

    deinit {
        tracker?.release()
    }
}

#Preview {
    ContentView()
}
