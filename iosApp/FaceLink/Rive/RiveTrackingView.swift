import SwiftUI
import KMPFaceLink
import RiveRuntime

/// Rive avatar tracking view driven by KMP face tracking data.
///
/// Uses `RiveParameterMapper` from KMP to convert face tracking data to
/// Rive State Machine inputs, then applies them to a `RiveViewModel`.
struct RiveTrackingView: View {
    @StateObject private var viewModel = RiveTrackingViewModel()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if viewModel.isRiveLoaded {
                viewModel.riveView()
                    .ignoresSafeArea()
            } else {
                VStack(spacing: 12) {
                    Text("Rive Avatar")
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(.white)
                    Text("Place sample_avatar.riv in Resources")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.6))
                }
            }

            // Controls overlay
            VStack {
                Spacer()

                VStack(spacing: 8) {
                    HStack {
                        VStack(alignment: .leading) {
                            Text("Rive: \(viewModel.riveStatus)")
                                .font(.system(.caption, design: .monospaced))
                                .foregroundStyle(.white.opacity(0.7))
                            Text(viewModel.isTracking ? "Tracking Active" : "Tracking Stopped")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(viewModel.isTracking ? .green : .gray)
                        }
                        Spacer()
                        Text("Frames: \(viewModel.frameCount)")
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(.white.opacity(0.6))
                    }
                    .padding(.horizontal)

                    Button(viewModel.isTracking ? "Stop" : "Start") {
                        viewModel.toggleTracking()
                    }
                    .buttonStyle(TrackingButtonStyle(isTracking: viewModel.isTracking))
                    .padding(.bottom, 40)
                }
                .padding()
                .background(.ultraThinMaterial)
            }
        }
    }
}

// MARK: - ViewModel

@MainActor
class RiveTrackingViewModel: ObservableObject {
    @Published var isTracking = false
    @Published var isRiveLoaded = false
    @Published var riveStatus = "Uninitialized"
    @Published var frameCount = 0

    private var tracker: FaceTracker?
    private var riveViewModel: RiveViewModel?
    private let mapper: RiveParameterMapper
    private var observeTask: Task<Void, Never>?

    init() {
        mapper = RiveDefaultMappings.shared.createMapper(config: RiveMapperConfig())

        let config = FaceTrackerConfig(
            smoothingConfig: SmoothingConfig.Ema(alpha: 0.4),
            enableCalibration: false,
            cameraFacing: .front
        )
        tracker = FaceTrackerFactory_iosKt.createFaceTracker(
            platformContext: PlatformContext(),
            config: config
        )

        loadRiveModel()
    }

    func riveView() -> some View {
        riveViewModel?.view() ?? RiveViewModel(fileName: "").view()
    }

    func toggleTracking() {
        if isTracking {
            stopTracking()
        } else {
            startTracking()
        }
    }

    private func loadRiveModel() {
        do {
            let vm = try RiveViewModel(fileName: "sample_avatar", stateMachineName: "State Machine 1")
            riveViewModel = vm
            isRiveLoaded = true
            riveStatus = "Ready"
        } catch {
            riveStatus = "Error: \(error.localizedDescription)"
        }
    }

    private func startTracking() {
        guard let tracker = tracker else { return }
        Task {
            do {
                try await tracker.start()
                isTracking = true
                riveStatus = "Tracking"
                observeData()
            } catch {
                riveStatus = "Error"
            }
        }
    }

    private func stopTracking() {
        observeTask?.cancel()
        observeTask = nil
        guard let tracker = tracker else { return }
        Task {
            do { try await tracker.stop() } catch { }
            isTracking = false
            riveStatus = "Stopped"
        }
    }

    private func observeData() {
        guard let tracker = tracker, let riveVM = riveViewModel else { return }

        observeTask = Task { [weak self, mapper] in
            for await data in tracker.trackingData {
                guard let self, !Task.isCancelled else { break }
                let inputs = mapper.map(data: data)
                for (name, input) in inputs {
                    if let numberInput = input as? RiveInput.Number {
                        try? riveVM.setInput(name, value: Double(numberInput.value))
                    } else if let boolInput = input as? RiveInput.BooleanInput {
                        try? riveVM.setInput(name, value: boolInput.value)
                    } else if input is RiveInput.Trigger {
                        try? riveVM.triggerInput(name)
                    }
                }
                self.frameCount += 1
            }
        }
    }

    deinit {
        observeTask?.cancel()
        tracker?.release()
    }
}
