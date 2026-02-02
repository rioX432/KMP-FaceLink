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
    private var observeTasks: [Task<Void, Never>] = []

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
        cancelObserveTasks()
        Task {
            try await tracker.stop()
            isTracking = false
            statusText = "Stopped"
        }
    }

    private func observeData() {
        guard let tracker = tracker else { return }

        let dataTask = Task { [weak self] in
            for await data in tracker.trackingData {
                guard let self, !Task.isCancelled else { break }
                let head = data.headTransform
                self.headRotationText = String(
                    format: "Head: P=%.1f Y=%.1f R=%.1f",
                    head.pitch, head.yaw, head.roll
                )
                let lines = data.blendShapes.sorted { $0.key.name < $1.key.name }
                    .map { "\($0.key.arKitName): \(String(format: "%.3f", $0.value.floatValue))" }
                self.blendShapesText = lines.joined(separator: "\n")
            }
        }

        let stateTask = Task { [weak self] in
            for await state in tracker.state {
                guard let self, !Task.isCancelled else { break }
                switch state {
                case .idle: self.statusText = "Idle"
                case .starting: self.statusText = "Starting..."
                case .tracking: self.statusText = "Tracking"
                case .stopped: self.statusText = "Stopped"
                case .error: self.statusText = "Error"
                default: self.statusText = "Unknown"
                }
            }
        }

        observeTasks = [dataTask, stateTask]
    }

    private func cancelObserveTasks() {
        observeTasks.forEach { $0.cancel() }
        observeTasks = []
    }

    deinit {
        cancelObserveTasks()
        tracker?.release()
    }
}

#Preview {
    ContentView()
}
