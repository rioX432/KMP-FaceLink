import SwiftUI
import ARKit
import KMPFaceLink

struct HolisticTrackingView: View {
    @StateObject private var viewModel = HolisticTrackingViewModel()
    @StateObject private var smoothingSettings = SmoothingSettings()
    @State private var showSmoothingSheet = false

    var body: some View {
        ZStack {
            // Camera preview from ARKit (face tracking uses TrueDepth sensor)
            if let session = viewModel.arSession {
                ARCameraView(
                    isRunning: $viewModel.isTracking,
                    showLandmarks: .constant(false),
                    externalSession: session
                )
                .ignoresSafeArea()
            } else {
                Color(.systemBackground).ignoresSafeArea()
            }

            // Status overlay
            VStack {
                HStack {
                    StatusBadge(text: viewModel.statusText, isTracking: viewModel.isTracking)
                    Spacer()
                    // Settings button
                    Button {
                        showSmoothingSheet = true
                    } label: {
                        Image(systemName: "gearshape")
                            .font(.body)
                            .foregroundStyle(.primary)
                            .padding(8)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                    .accessibilityLabel("Smoothing settings")
                    // Modality indicators
                    HStack(spacing: 8) {
                        ModalityDot(label: "F", active: viewModel.faceActive)
                            .accessibilityLabel("Face tracking: \(viewModel.faceActive ? "active" : "inactive")")
                        ModalityDot(label: "H", active: viewModel.handActive)
                            .accessibilityLabel("Hand tracking: \(viewModel.handActive ? "active" : "inactive")")
                        ModalityDot(label: "B", active: viewModel.bodyActive)
                            .accessibilityLabel("Body tracking: \(viewModel.bodyActive ? "active" : "inactive")")
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.ultraThinMaterial, in: Capsule())
                }
                .padding()

                Spacer()

                // Summary info
                Text(viewModel.summaryText)
                    .font(.system(.body, design: .monospaced))
                    .dynamicTypeSize(...DynamicTypeSize.accessibility1)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal)

                // Control button
                Button(viewModel.isTracking ? "Stop" : "Start") {
                    withAnimation {
                        viewModel.toggleTracking()
                    }
                }
                .buttonStyle(TrackingButtonStyle(isTracking: viewModel.isTracking))
                .padding(.bottom)
            }
        }
        .sheet(isPresented: $showSmoothingSheet) {
            SmoothingSettingsView(settings: smoothingSettings) { config in
                viewModel.updateSmoothing(config)
                showSmoothingSheet = false
            }
            .presentationDetents([.medium, .large])
        }
    }
}

struct ModalityDot: View {
    let label: String
    let active: Bool

    var body: some View {
        Text(label)
            .font(.system(.caption, design: .monospaced).weight(.bold))
            .dynamicTypeSize(...DynamicTypeSize.accessibility1)
            .foregroundStyle(active ? .green : .gray)
    }
}

// MARK: - ViewModel

@MainActor
class HolisticTrackingViewModel: ObservableObject {
    @Published var statusText = "Idle"
    @Published var summaryText = "Tap Start to begin holistic tracking..."
    @Published var isTracking = false
    @Published var faceActive = false
    @Published var handActive = false
    @Published var bodyActive = false
    @Published var arSession: ARSession?

    private var tracker: HolisticTracker?
    private var observeTasks: [Task<Void, Never>] = []

    init() {
        let config = HolisticTrackerConfig(
            enableFace: true,
            enableHand: true,
            enableBody: true,
            faceConfig: FaceTrackerConfig(
                smoothingConfig: SmoothingConfig.Ema(alpha: 0.4),
                enhancerConfig: BlendShapeEnhancerConfig.None(),
                calibrationConfig: CalibrationConfig(),
                cameraFacing: .front
            ),
            handConfig: HandTrackerConfig(
                smoothingConfig: SmoothingConfig.Ema(alpha: 0.4),
                maxHands: 2,
                enableGestureRecognition: true,
                cameraFacing: .front
            ),
            bodyConfig: BodyTrackerConfig(
                smoothingConfig: SmoothingConfig.Ema(alpha: 0.4),
                maxBodies: 1,
                cameraFacing: .front
            ),
            cameraFacing: .front
        )
        tracker = HolisticTrackerFactory_iosKt.createHolisticTracker(
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
            do {
                try await tracker.start()
                arSession = HolisticTrackerSessionKt.getARSession(tracker)
                isTracking = true
                statusText = "Tracking"
                observeData()
            } catch {
                statusText = "Error: \(error.localizedDescription)"
            }
        }
    }

    private func stopTracking() {
        guard let tracker = tracker else { return }
        cancelObserveTasks()
        Task {
            do {
                try await tracker.stop()
            } catch {
                // Best-effort stop
            }
            isTracking = false
            statusText = "Stopped"
            faceActive = false
            handActive = false
            bodyActive = false
        }
    }

    private func observeData() {
        guard let tracker = tracker else { return }

        let dataTask = Task { [weak self] in
            for await data in tracker.trackingData {
                guard let self, !Task.isCancelled else { break }

                self.faceActive = data.face?.isTracking == true
                self.handActive = data.hand?.isTracking == true
                self.bodyActive = data.body?.isTracking == true

                var lines: [String] = []
                if let face = data.face, face.isTracking {
                    let head = face.headTransform
                    lines.append(String(
                        format: "Face: P%+.1f Y%+.1f R%+.1f",
                        head.pitch, head.yaw, head.roll
                    ))
                }
                if let hand = data.hand, hand.isTracking {
                    lines.append("Hands: \(hand.hands.count)")
                }
                if let body = data.body, body.isTracking {
                    lines.append("Bodies: \(body.bodies.count)")
                }
                self.summaryText = lines.isEmpty ? "No subjects detected" : lines.joined(separator: "  |  ")
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
