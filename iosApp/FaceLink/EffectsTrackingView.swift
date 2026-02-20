import SwiftUI
import KMPFaceLink

struct EffectsTrackingView: View {
    @StateObject private var viewModel = EffectsTrackingViewModel()

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

                // Effect toggles
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(viewModel.availableEffects, id: \.self) { effectId in
                            let isEnabled = viewModel.enabledEffects.contains(effectId)
                            Button {
                                viewModel.toggleEffect(effectId)
                            } label: {
                                Text(effectId)
                                    .font(.caption2)
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(
                                        isEnabled ? Color.blue.opacity(0.6) : Color.white.opacity(0.15),
                                        in: Capsule()
                                    )
                            }
                        }
                    }
                    .padding(.horizontal)
                }

                Spacer()

                // Active effects panel
                VStack(alignment: .leading, spacing: 4) {
                    Text("Active Effects").font(.caption.weight(.bold))
                    if viewModel.activeEffects.isEmpty {
                        Text("No active effects")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.activeEffects, id: \.id) { effect in
                            HStack {
                                Text("\(effect.id) (\(effect.type.name))")
                                    .font(.system(.caption2, design: .monospaced))
                                    .foregroundStyle(.secondary)
                                GeometryReader { geo in
                                    ZStack(alignment: .leading) {
                                        Rectangle().fill(.white.opacity(0.1))
                                        Rectangle()
                                            .fill(.green)
                                            .frame(width: max(0, geo.size.width * CGFloat(effect.intensity)))
                                    }
                                }
                                .frame(height: 6)
                            }
                        }
                    }
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

// MARK: - ViewModel

@MainActor
class EffectsTrackingViewModel: ObservableObject {
    @Published var statusText = "Idle"
    @Published var isTracking = false
    @Published var arSession: ARSession?
    @Published var frameTimestamp: Int64 = 0
    @Published var enabledEffects: Set<String> = []
    @Published var activeEffects: [ActiveEffect] = []

    let availableEffects = ["catEars", "glasses", "smileHearts", "winkSparkle", "cartoonEyes", "openPalmParticles"]

    private var tracker: FaceTracker?
    private var engine: EffectEngine?
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
        engine = EffectEngine()
    }

    func toggleTracking() {
        if isTracking { stopTracking() } else { startTracking() }
    }

    func toggleEffect(_ effectId: String) {
        guard let engine = engine else { return }
        Task {
            if enabledEffects.contains(effectId) {
                let _ = try? await engine.removeEffect(id: effectId)
                enabledEffects.remove(effectId)
            } else {
                if let effect = createEffect(id: effectId) {
                    try? await engine.addEffect(effect: effect)
                    enabledEffects.insert(effectId)
                }
            }
        }
    }

    private func startTracking() {
        guard let tracker = tracker else { return }
        Task {
            do {
                try await tracker.start()
                arSession = FaceTrackerARSessionKt.getARSession(tracker)
                isTracking = true
                statusText = "Tracking"
                observeData()
            } catch {
                statusText = "Error: \(error.localizedDescription)"
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
        guard let tracker = tracker, let engine = engine else { return }

        let task = Task { [weak self] in
            for await data in tracker.trackingData {
                guard let self, !Task.isCancelled else { break }
                self.frameTimestamp = data.timestampMs
                let output = try? await engine.processFace(data: data)
                if let output = output {
                    self.activeEffects = output.activeEffects
                }
            }
        }
        observeTasks = [task]
    }

    private func createEffect(id: String) -> Effect? {
        switch id {
        case "catEars": return BuiltInEffectsKt.catEarsEffect(id: id)
        case "glasses": return BuiltInEffectsKt.glassesEffect(id: id)
        case "smileHearts": return BuiltInEffectsKt.smileHeartsEffect(id: id)
        case "winkSparkle": return BuiltInEffectsKt.winkSparkleEffect(id: id)
        case "cartoonEyes": return BuiltInEffectsKt.cartoonEyesEffect(id: id)
        case "openPalmParticles": return BuiltInEffectsKt.openPalmParticlesEffect(id: id)
        default: return nil
        }
    }

    private func cancelObserveTasks() {
        observeTasks.forEach { $0.cancel() }
        observeTasks = []
    }

    deinit {
        observeTasks.forEach { $0.cancel() }
        engine?.release()
        tracker?.release()
    }
}
