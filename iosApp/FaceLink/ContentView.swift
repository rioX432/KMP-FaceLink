import SwiftUI
import ARKit
import KMPFaceLink

enum TrackingMode: String, CaseIterable {
    case face = "Face"
    case hand = "Hand"
    case holistic = "Holistic"
    case avatar = "Avatar"
}

struct ContentView: View {
    @State private var selectedMode: TrackingMode = .face

    var body: some View {
        VStack(spacing: 0) {
            // Mode picker
            Picker("Mode", selection: $selectedMode) {
                ForEach(TrackingMode.allCases, id: \.self) { mode in
                    Text(mode.rawValue).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.top, 8)
            .background(Color.black)

            // Content
            switch selectedMode {
            case .face:
                FaceTrackingContentView()
            case .hand:
                HandTrackingView()
            case .holistic:
                HolisticTrackingView()
            case .avatar:
                AvatarTrackingView()
            }
        }
    }
}

struct FaceTrackingContentView: View {
    @StateObject private var viewModel = FaceTrackingViewModel()
    @StateObject private var smoothingSettings = SmoothingSettings()
    @State private var showBlendShapes = false
    @State private var showLandmarks = true
    @State private var showSettings = false

    var body: some View {
        ZStack {
            // Camera preview — uses tracker's ARSession to avoid dual-session conflict
            ARCameraView(
                isRunning: $viewModel.isTracking,
                showLandmarks: $showLandmarks,
                externalSession: viewModel.arSession
            )
            .ignoresSafeArea()

            // Tracking overlay
            VStack {
                // Status bar at top
                HStack {
                    StatusBadge(text: viewModel.statusText, isTracking: viewModel.isTracking)
                    Spacer()
                    HStack(spacing: 16) {
                        Button {
                            showSettings = true
                        } label: {
                            Image(systemName: "slider.horizontal.3")
                                .font(.title2)
                                .foregroundStyle(.white)
                        }
                        Button {
                            showLandmarks.toggle()
                        } label: {
                            Image(systemName: showLandmarks ? "face.smiling.fill" : "face.smiling")
                                .font(.title2)
                                .foregroundStyle(showLandmarks ? .cyan : .white)
                        }
                        Button {
                            showBlendShapes.toggle()
                        } label: {
                            Image(systemName: showBlendShapes ? "list.bullet.circle.fill" : "list.bullet.circle")
                                .font(.title2)
                                .foregroundStyle(.white)
                        }
                    }
                }
                .padding()

                Spacer()

                // Head rotation display
                HeadRotationView(text: viewModel.headRotationText)
                    .padding(.horizontal)

                // Blend shapes panel (collapsible)
                if showBlendShapes {
                    BlendShapesPanel(text: viewModel.blendShapesText)
                        .frame(maxHeight: 200)
                        .padding(.horizontal)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }

                // Control button
                Button(viewModel.isTracking ? "Stop" : "Start") {
                    withAnimation {
                        viewModel.toggleTracking()
                    }
                }
                .buttonStyle(TrackingButtonStyle(isTracking: viewModel.isTracking))
                .padding(.bottom, 40)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: showBlendShapes)
        .animation(.easeInOut(duration: 0.2), value: showLandmarks)
        .sheet(isPresented: $showSettings) {
            SmoothingSettingsView(settings: smoothingSettings) { config in
                viewModel.updateSmoothing(config)
                showSettings = false
            }
            .presentationDetents([.medium, .large])
        }
    }
}

// MARK: - Subviews

struct StatusBadge: View {
    let text: String
    let isTracking: Bool

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(isTracking ? .green : .gray)
                .frame(width: 8, height: 8)
            Text(text)
                .font(.subheadline.weight(.medium))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(.ultraThinMaterial, in: Capsule())
    }
}

struct HeadRotationView: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(.body, design: .monospaced))
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}

struct BlendShapesPanel: View {
    let text: String

    var body: some View {
        ScrollView {
            Text(text)
                .font(.system(.caption, design: .monospaced))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
        }
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}

struct TrackingButtonStyle: ButtonStyle {
    let isTracking: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .frame(width: 100, height: 50)
            .background(isTracking ? .red : .blue, in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
    }
}

// MARK: - ViewModel

@MainActor
class FaceTrackingViewModel: ObservableObject {
    @Published var statusText = "Idle"
    @Published var headRotationText = "P: 0.0  Y: 0.0  R: 0.0"
    @Published var blendShapesText = "Tap Start to begin tracking..."
    @Published var isTracking = false
    @Published var arSession: ARSession?

    private var tracker: FaceTracker?
    private var observeTasks: [Task<Void, Never>] = []

    init() {
        let companion = BlendShapeEnhancerConfig.Companion.shared
        let config = FaceTrackerConfig(
            smoothingConfig: SmoothingConfig.Ema(alpha: 0.4),
            enhancerConfig: BlendShapeEnhancerConfig.Default(
                sensitivityOverrides: companion.defaultSensitivityMap,
                deadZoneOverrides: companion.defaultDeadZoneMap,
                geometricBlendWeight: 0.7
            ),
            enableCalibration: false,
            cameraFacing: .front
        )
        tracker = FaceTrackerFactory_iosKt.createFaceTracker(
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

    func updateSmoothing(_ config: SmoothingConfig) {
        tracker?.updateSmoothing(config: config)
    }

    private func startTracking() {
        guard let tracker = tracker else { return }
        Task {
            try await tracker.start()
            arSession = FaceTrackerARSessionKt.getARSession(tracker)
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
                    format: "P: %+.1f  Y: %+.1f  R: %+.1f",
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
                case .released: self.statusText = "Released"
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
        observeTasks.forEach { $0.cancel() }
        tracker?.release()
    }
}

#Preview {
    ContentView()
}
