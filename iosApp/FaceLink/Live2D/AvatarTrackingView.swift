import SwiftUI
import MetalKit
import KMPFaceLink

/// SwiftUI view showing a Live2D avatar driven by ARKit face tracking.
struct AvatarTrackingView: View {
    @StateObject private var viewModel = AvatarTrackingViewModel()

    var body: some View {
        if !CubismBridge.sdkAvailable {
            Live2DUnavailableView()
        } else {
            ZStack {
                if viewModel.isLive2DReady {
                    Live2DMetalView(
                        bridge: viewModel.cubismBridge!,
                        commandQueue: viewModel.commandQueue!
                    )
                    .ignoresSafeArea()
                } else {
                    Color.black
                        .ignoresSafeArea()
                    VStack {
                        Text("Live2D: Loading...")
                            .foregroundStyle(.white)
                    }
                }

                VStack {
                    HStack {
                        StatusBadge(text: viewModel.statusText, isTracking: viewModel.isTracking)
                        Spacer()
                    }
                    .padding()

                    Spacer()

                    HeadRotationView(text: viewModel.headRotationText)
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
}

/// Fallback view when the Live2D SDK is not installed.
struct Live2DUnavailableView: View {
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "person.crop.square")
                    .font(.system(size: 48))
                    .foregroundStyle(.gray)
                Text("Live2D SDK Not Available")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.white)
                Text("Run scripts/setup-live2d.sh to install the SDK.")
                    .font(.subheadline)
                    .foregroundStyle(.gray)
                    .multilineTextAlignment(.center)
            }
            .padding()
        }
    }
}

// MARK: - ViewModel

@MainActor
class AvatarTrackingViewModel: ObservableObject {
    @Published var statusText = "Idle"
    @Published var headRotationText = "P: 0.0  Y: 0.0  R: 0.0"
    @Published var isTracking = false
    @Published var isLive2DReady = false

    private(set) var cubismBridge: CubismBridge?
    private(set) var commandQueue: MTLCommandQueue?

    private var tracker: FaceTracker?
    private var mapper: Live2DParameterMapper?
    private var observeTasks: [Task<Void, Never>] = []

    init() {
        setupTracker()
        setupLive2D()
    }

    func toggleTracking() {
        if isTracking {
            stopTracking()
        } else {
            startTracking()
        }
    }

    // MARK: - Private

    private func setupTracker() {
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
        mapper = Live2DParameterMapper(config: Live2DMapperConfig(
            blendShapeOverrides: [:],
            customMappings: [:]
        ))
    }

    private func setupLive2D() {
        guard CubismBridge.sdkAvailable else {
            statusText = "SDK not available"
            return
        }

        guard let device = MTLCreateSystemDefaultDevice() else {
            statusText = "Metal not available"
            return
        }
        commandQueue = device.makeCommandQueue()

        cubismBridge = CubismBridge(device: device)

        // Load Hiyori model from bundle
        if let modelDir = Bundle.main.path(forResource: "Hiyori", ofType: nil, inDirectory: "Live2D") {
            let loaded = cubismBridge?.loadModel(fromDirectory: modelDir, modelFileName: "Hiyori.model3.json") ?? false
            isLive2DReady = loaded
            if !loaded {
                statusText = "Model load failed"
            }
        } else {
            statusText = "Model not found"
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
        cancelObserveTasks()
        guard let tracker = tracker else { return }
        Task {
            try await tracker.stop()
            isTracking = false
            statusText = "Stopped"
        }
    }

    private func observeData() {
        guard let tracker = tracker,
              let mapper = mapper,
              let bridge = cubismBridge else { return }

        // Head rotation display + parameter mapping
        let dataTask = Task { [weak self] in
            for await data in tracker.trackingData {
                guard let self, !Task.isCancelled else { break }

                let head = data.headTransform
                self.headRotationText = String(
                    format: "P: %+.1f  Y: %+.1f  R: %+.1f",
                    head.pitch, head.yaw, head.roll
                )

                // Map tracking data to Live2D parameters (thread-safe batch update)
                guard data.isTracking else { continue }
                let params = mapper.map(data: data)
                if let nsParams = params as? [String: NSNumber] {
                    bridge.setParameters(nsParams)
                }
            }
        }

        // Tracker state updates
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
        cubismBridge?.releaseResources()
        tracker?.release()
    }
}
