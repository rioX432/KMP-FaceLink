import SwiftUI
import AVFoundation
import SampleShared

/// Hand tracking view with camera preview and landmark overlay
struct HandTrackingView: View {
    @StateObject private var viewModel = HandTrackingViewModel()
    @StateObject private var smoothingSettings = SmoothingSettings()
    @State private var showSmoothingSheet = false

    var body: some View {
        ZStack {
            // Camera preview background — intentionally fills the full screen including safe areas
            HandCameraPreview(session: viewModel.captureSession)
                .ignoresSafeArea()

            // Controls overlay — SwiftUI's built-in safe area handling keeps content clear
            // of the notch, Dynamic Island, and home indicator in any orientation.
            VStack {
                // Status bar
                HStack {
                    StatusBadge(text: viewModel.statusText, isTracking: viewModel.isTracking)
                    Spacer()
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
                }
                .padding(.horizontal, 16)

                Spacer()

                // Gesture display
                if viewModel.isTracking {
                    let isIdle = viewModel.gestureText == "No gesture"
                    Text(viewModel.gestureText)
                        .font(.system(.title2, design: .monospaced))
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                        .foregroundStyle(isIdle ? .secondary : .primary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                        .opacity(isIdle ? 0.6 : 1.0)
                        .padding(.horizontal, 16)
                }

                // Hand count
                Text(viewModel.handsText)
                    .font(.system(.body, design: .monospaced))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 16)

                // Control button
                Button(viewModel.isTracking ? "Stop" : "Start") {
                    withAnimation {
                        viewModel.toggleTracking()
                    }
                }
                .buttonStyle(TrackingButtonStyle(isTracking: viewModel.isTracking))
                .accessibilityLabel(viewModel.isTracking ? "Stop hand tracking" : "Start hand tracking")
                .accessibilityHint("Double tap to toggle hand tracking")
                .padding(.bottom)
            }

            // Hand landmark overlay — fills the full screen to match the camera preview
            if viewModel.isTracking, let data = viewModel.latestData {
                HandLandmarkOverlayView(trackingData: data)
                    .ignoresSafeArea()
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

// MARK: - ViewModel

@MainActor
class HandTrackingViewModel: ObservableObject {
    @Published var statusText = "Idle"
    @Published var gestureText = "No gesture"
    @Published var handsText = "No hands detected"
    @Published var isTracking = false
    @Published var latestData: HandTrackingData? = nil

    @Published private(set) var captureSession: AVCaptureSession?

    private var tracker: HandTracker?
    private var observeTasks: [Task<Void, Never>] = []

    init() {
        let handTracker = SampleTrackerConfigsKt.createSampleHandTracker()
        self.tracker = handTracker

        // Capture session is set after start() — see startTracking()
        self.captureSession = nil
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
                self.captureSession = HandTrackerCaptureSessionKt.getCaptureSession(tracker)
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
            latestData = nil
        }
    }

    private func observeData() {
        guard let tracker = tracker else { return }

        let dataTask = Task { [weak self] in
            for await data in tracker.trackingData {
                guard let self, !Task.isCancelled else { break }
                self.latestData = data

                if data.isTracking {
                    let count = data.hands.count
                    self.handsText = "Hands: \(count)"

                    let gestures = data.hands.map { hand in
                        let h = hand.handedness.name
                        let g = hand.gesture != .none
                            ? "\(hand.gesture.name) (\(Int(hand.gestureConfidence * 100))%)"
                            : "—"
                        return "\(h): \(g)"
                    }
                    self.gestureText = gestures.joined(separator: " | ")
                } else {
                    self.handsText = "No hands detected"
                    self.gestureText = "No gesture"
                }
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
        observeTasks.forEach { $0.cancel() }
        tracker?.release()
    }
}

// MARK: - Camera Preview

struct HandCameraPreview: UIViewRepresentable {
    let session: AVCaptureSession?

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        // Attach preview layer when session becomes available
        if let session = session, context.coordinator.previewLayer == nil {
            let previewLayer = AVCaptureVideoPreviewLayer(session: session)
            previewLayer.videoGravity = .resizeAspectFill
            previewLayer.frame = uiView.bounds
            uiView.layer.addSublayer(previewLayer)
            context.coordinator.previewLayer = previewLayer
        }
        context.coordinator.previewLayer?.frame = uiView.bounds
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    class Coordinator {
        var previewLayer: AVCaptureVideoPreviewLayer?
    }
}

// MARK: - Hand Landmark Overlay

struct HandLandmarkOverlayView: View {
    let trackingData: HandTrackingData

    var body: some View {
        Canvas { context, size in
            for hand in trackingData.hands {
                drawHand(hand: hand, in: context, size: size)
            }
        }
    }

    private func drawHand(hand: TrackedHand, in context: GraphicsContext, size: CGSize) {
        let landmarks = hand.landmarks
        guard !landmarks.isEmpty else { return }

        // Scale overlay sizes relative to view dimensions
        let minDim = min(size.width, size.height)
        let boneWidth = minDim * 0.005
        let jointRadius = minDim * 0.01

        let landmarkMap = Dictionary(uniqueKeysWithValues: landmarks.map { ($0.joint, $0) })

        func pos(_ joint: HandJoint) -> CGPoint? {
            guard let lm = landmarkMap[joint] else { return nil }
            // Vision coordinates are already in normalized space [0,1]
            // Flip x for front camera mirror
            return CGPoint(
                x: CGFloat(1.0 - lm.x) * size.width,
                y: CGFloat(lm.y) * size.height
            )
        }

        // Draw bones
        let boneColor = Color.green.opacity(0.7)

        func drawBone(_ from: HandJoint, _ to: HandJoint) {
            guard let a = pos(from), let b = pos(to) else { return }
            var path = Path()
            path.move(to: a)
            path.addLine(to: b)
            context.stroke(path, with: .color(boneColor), lineWidth: boneWidth)
        }

        // Wrist connections
        drawBone(.wrist, .thumbCmc)
        drawBone(.wrist, .indexFingerMcp)
        drawBone(.wrist, .middleFingerMcp)
        drawBone(.wrist, .ringFingerMcp)
        drawBone(.wrist, .pinkyMcp)

        // Thumb
        drawBone(.thumbCmc, .thumbMcp)
        drawBone(.thumbMcp, .thumbIp)
        drawBone(.thumbIp, .thumbTip)

        // Index
        drawBone(.indexFingerMcp, .indexFingerPip)
        drawBone(.indexFingerPip, .indexFingerDip)
        drawBone(.indexFingerDip, .indexFingerTip)

        // Middle
        drawBone(.middleFingerMcp, .middleFingerPip)
        drawBone(.middleFingerPip, .middleFingerDip)
        drawBone(.middleFingerDip, .middleFingerTip)

        // Ring
        drawBone(.ringFingerMcp, .ringFingerPip)
        drawBone(.ringFingerPip, .ringFingerDip)
        drawBone(.ringFingerDip, .ringFingerTip)

        // Pinky
        drawBone(.pinkyMcp, .pinkyPip)
        drawBone(.pinkyPip, .pinkyDip)
        drawBone(.pinkyDip, .pinkyTip)

        // Palm connections
        drawBone(.indexFingerMcp, .middleFingerMcp)
        drawBone(.middleFingerMcp, .ringFingerMcp)
        drawBone(.ringFingerMcp, .pinkyMcp)

        // Draw joint dots
        let tipJoints: Set<HandJoint> = [.thumbTip, .indexFingerTip, .middleFingerTip, .ringFingerTip, .pinkyTip]

        for lm in landmarks {
            guard let p = pos(lm.joint) else { continue }
            let color: Color = tipJoints.contains(lm.joint) ? .red : .cyan
            let rect = CGRect(x: p.x - jointRadius, y: p.y - jointRadius, width: jointRadius * 2, height: jointRadius * 2)
            context.fill(Path(ellipseIn: rect), with: .color(color))
        }
    }
}

#Preview {
    HandTrackingView()
}
