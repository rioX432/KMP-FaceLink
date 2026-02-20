import SwiftUI
import KMPFaceLink

struct ActionsTrackingView: View {
    @StateObject private var viewModel = ActionsTrackingViewModel()

    var body: some View {
        ZStack {
            if let session = viewModel.arSession {
                ARCameraView(
                    isRunning: $viewModel.isTracking,
                    showLandmarks: .constant(false),
                    externalSession: session
                )
                .ignoresSafeArea()
            }

            VStack {
                HStack {
                    StatusBadge(text: viewModel.statusText, isTracking: viewModel.isTracking)
                    if viewModel.isTracking {
                        FpsOverlayView(frameTimestamp: viewModel.frameTimestamp)
                    }
                    Spacer()
                }
                .padding()

                if let error = viewModel.errorMessage {
                    ErrorOverlayView(message: error) {
                        viewModel.toggleTracking()
                    }
                }

                Spacer()

                // Bottom panel
                VStack(alignment: .leading, spacing: 8) {
                    // Emotions
                    Text("Emotions").font(.caption.weight(.bold))
                    EmotionBarsView(scores: viewModel.emotionScores, dominant: viewModel.dominantEmotion)

                    Divider().background(.white.opacity(0.3))

                    // Triggers
                    Text("Triggers").font(.caption.weight(.bold))
                    TriggerLedsView(activeTriggers: viewModel.activeTriggers)

                    Divider().background(.white.opacity(0.3))

                    // Recording
                    Text("Recording").font(.caption.weight(.bold))
                    RecordingControlsView(
                        isRecording: viewModel.isRecording,
                        sessionInfo: viewModel.sessionInfo,
                        onToggle: { viewModel.toggleRecording() }
                    )
                }
                .padding(12)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal)

                Button(viewModel.isTracking ? "Stop" : "Start") {
                    viewModel.toggleTracking()
                }
                .buttonStyle(TrackingButtonStyle(isTracking: viewModel.isTracking))
                .padding(.bottom, 40)
            }
        }
    }
}

struct EmotionBarsView: View {
    let scores: [String: Float]
    let dominant: String

    var body: some View {
        VStack(spacing: 2) {
            ForEach(Array(scores.keys.sorted()), id: \.self) { emotion in
                let value = scores[emotion] ?? 0
                let isDominant = emotion == dominant
                HStack(spacing: 4) {
                    Text(emotion)
                        .font(.system(.caption2, design: .monospaced))
                        .foregroundStyle(isDominant ? .green : .secondary)
                        .frame(width: 80, alignment: .leading)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Rectangle().fill(.white.opacity(0.1))
                            Rectangle()
                                .fill(isDominant ? .green : .orange)
                                .frame(width: max(0, geo.size.width * CGFloat(min(1, max(0, value)))))
                        }
                    }
                    .frame(height: 6)
                }
            }
        }
    }
}

struct TriggerLedsView: View {
    let activeTriggers: Set<String>

    private let triggerNames = ["smile", "winkL", "winkR", "tongueOut", "surprised"]

    var body: some View {
        HStack(spacing: 12) {
            ForEach(triggerNames, id: \.self) { name in
                VStack(spacing: 2) {
                    Circle()
                        .fill(activeTriggers.contains(name) ? .green : .gray)
                        .frame(width: 10, height: 10)
                    Text(name)
                        .font(.system(.caption2, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

struct RecordingControlsView: View {
    let isRecording: Bool
    let sessionInfo: String?
    let onToggle: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(isRecording ? "Stop Rec" : "Record") {
                onToggle()
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(isRecording ? .red : .blue, in: Capsule())

            if let info = sessionInfo {
                Text(info)
                    .font(.system(.caption2, design: .monospaced))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - ViewModel

@MainActor
class ActionsTrackingViewModel: ObservableObject {
    @Published var statusText = "Idle"
    @Published var isTracking = false
    @Published var arSession: ARSession?
    @Published var errorMessage: String?
    @Published var frameTimestamp: Int64 = 0
    @Published var emotionScores: [String: Float] = [:]
    @Published var dominantEmotion: String = "NEUTRAL"
    @Published var activeTriggers: Set<String> = []
    @Published var isRecording = false
    @Published var sessionInfo: String?

    private var tracker: FaceTracker?
    private var actionSystem: ActionSystem?
    private var emotionClassifier: EmotionClassifier?
    private var recorder: TrackingRecorder?
    private var observeTasks: [Task<Void, Never>] = []

    init() {
        tracker = FaceTrackerFactory_iosKt.createFaceTracker(
            platformContext: PlatformContext(),
            config: FaceTrackerConfig(
                smoothingConfig: SmoothingConfig.Ema(alpha: 0.4),
                enhancerConfig: nil,
                enableCalibration: false,
                cameraFacing: .front
            )
        )
        actionSystem = ActionSystem()
        emotionClassifier = EmotionClassifier(neutralThreshold: 0.15, templates: EmotionClassifier.companion.DEFAULT_EMOTION_TEMPLATES)
        recorder = TrackingRecorder(maxFrames: 0)

        Task {
            await registerTriggers()
        }
    }

    func toggleTracking() {
        if isTracking { stopTracking() } else { startTracking() }
    }

    func toggleRecording() {
        guard let recorder = recorder else { return }
        Task {
            if isRecording {
                let session = try await recorder.stop()
                isRecording = false
                sessionInfo = "\(session.frameCount) frames, \(session.durationMs)ms"
            } else {
                try await recorder.start()
                isRecording = true
                sessionInfo = nil
            }
        }
    }

    private func startTracking() {
        guard let tracker = tracker else { return }
        errorMessage = nil
        Task {
            do {
                try await tracker.start()
                arSession = FaceTrackerARSessionKt.getARSession(tracker)
                isTracking = true
                statusText = "Tracking"
                observeData()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func stopTracking() {
        cancelObserveTasks()
        guard let tracker = tracker else { return }
        Task {
            do { try await tracker.stop() } catch {}
            isTracking = false
            statusText = "Stopped"
        }
    }

    private func observeData() {
        guard let tracker = tracker else { return }

        let dataTask = Task { [weak self] in
            for await data in tracker.trackingData {
                guard let self, !Task.isCancelled else { break }
                self.frameTimestamp = data.timestampMs

                // Emotion classification
                if let result = self.emotionClassifier?.classify(data: data) {
                    var scores: [String: Float] = [:]
                    for (key, value) in result.scores {
                        if let emotion = key as? Emotion {
                            scores[emotion.name] = value.floatValue
                        }
                    }
                    self.emotionScores = scores
                    self.dominantEmotion = result.emotion.name
                }

                // Action processing
                let _ = try? await self.actionSystem?.processFace(data: data)

                // Recording
                if let recorder = self.recorder, recorder.isRecording {
                    try? await recorder.record(data: data)
                }
            }
        }

        let eventsTask = Task { [weak self] in
            guard let system = self?.actionSystem else { return }
            for await event in system.events {
                guard let self, !Task.isCancelled else { break }
                if event is ActionEvent.Started {
                    self.activeTriggers.insert(event.actionId)
                } else if event is ActionEvent.Released {
                    self.activeTriggers.remove(event.actionId)
                }
            }
        }

        observeTasks = [dataTask, eventsTask]
    }

    private func registerTriggers() async {
        guard let system = actionSystem else { return }
        do {
            try await system.register(binding: ActionBinding(actionId: "smile", trigger: BuiltInTriggersKt.smileTrigger(threshold: 0.6), holdTimeMs: 0, cooldownMs: 0, debounceMs: 0, emitHeldEvents: false))
            try await system.register(binding: ActionBinding(actionId: "winkL", trigger: BuiltInTriggersKt.winkLeftTrigger(blinkThreshold: 0.6), holdTimeMs: 0, cooldownMs: 0, debounceMs: 0, emitHeldEvents: false))
            try await system.register(binding: ActionBinding(actionId: "winkR", trigger: BuiltInTriggersKt.winkRightTrigger(blinkThreshold: 0.6), holdTimeMs: 0, cooldownMs: 0, debounceMs: 0, emitHeldEvents: false))
            try await system.register(binding: ActionBinding(actionId: "tongueOut", trigger: BuiltInTriggersKt.tongueOutTrigger(threshold: 0.6), holdTimeMs: 0, cooldownMs: 0, debounceMs: 0, emitHeldEvents: false))
            try await system.register(binding: ActionBinding(actionId: "surprised", trigger: BuiltInTriggersKt.surprisedTrigger(threshold: 0.6), holdTimeMs: 0, cooldownMs: 0, debounceMs: 0, emitHeldEvents: false))
        } catch {
            // Ignore registration errors
        }
    }

    private func cancelObserveTasks() {
        observeTasks.forEach { $0.cancel() }
        observeTasks = []
    }

    deinit {
        observeTasks.forEach { $0.cancel() }
        actionSystem?.release()
        tracker?.release()
    }
}
