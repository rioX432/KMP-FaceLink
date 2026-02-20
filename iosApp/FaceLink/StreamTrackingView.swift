import SwiftUI
import KMPFaceLink

struct StreamTrackingView: View {
    @StateObject private var viewModel = StreamTrackingViewModel()

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

                Spacer()

                // Bottom panel
                VStack(alignment: .leading, spacing: 8) {
                    // Stream state
                    Text(viewModel.streamStateText)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(viewModel.streamStateColor)

                    // Connection form
                    HStack(spacing: 8) {
                        TextField("Host", text: $viewModel.host)
                            .textFieldStyle(.roundedBorder)
                            .font(.caption)
                        TextField("Port", text: $viewModel.port)
                            .textFieldStyle(.roundedBorder)
                            .font(.caption)
                            .frame(width: 80)
                    }
                    HStack(spacing: 8) {
                        TextField("Plugin Name", text: $viewModel.pluginName)
                            .textFieldStyle(.roundedBorder)
                            .font(.caption)
                        TextField("Developer", text: $viewModel.devName)
                            .textFieldStyle(.roundedBorder)
                            .font(.caption)
                    }

                    HStack(spacing: 8) {
                        Button(viewModel.isConnected ? "Disconnect" : "Connect") {
                            if viewModel.isConnected {
                                viewModel.disconnect()
                            } else {
                                viewModel.connect()
                            }
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(viewModel.isConnected ? .red : .blue, in: Capsule())
                    }

                    // Param preview
                    if !viewModel.paramPreview.isEmpty {
                        Text("Parameters (\(viewModel.paramPreview.count))")
                            .font(.caption.weight(.bold))
                        ForEach(Array(viewModel.paramPreview.prefix(8)), id: \.key) { key, value in
                            Text("\(key): \(String(format: "%.3f", value))")
                                .font(.system(.caption2, design: .monospaced))
                                .foregroundStyle(.secondary)
                        }
                        if viewModel.paramPreview.count > 8 {
                            Text("... +\(viewModel.paramPreview.count - 8) more")
                                .font(.caption2).foregroundStyle(.secondary)
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
class StreamTrackingViewModel: ObservableObject {
    @AppStorage("stream_host") var host = "localhost"
    @AppStorage("stream_port") var port = "8001"
    @AppStorage("stream_plugin") var pluginName = "KMP-FaceLink"
    @AppStorage("stream_dev") var devName = "FaceLink Dev"
    @AppStorage("stream_token") var savedToken = ""

    @Published var statusText = "Idle"
    @Published var isTracking = false
    @Published var arSession: ARSession?
    @Published var frameTimestamp: Int64 = 0
    @Published var streamStateText = "Disconnected"
    @Published var streamStateColor: Color = .gray
    @Published var isConnected = false
    @Published var paramPreview: [(key: String, value: Float)] = []

    private var tracker: FaceTracker?
    private var client: VtsStreamClient?
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
    }

    func toggleTracking() {
        if isTracking { stopTracking() } else { startTracking() }
    }

    func connect() {
        let token: String? = savedToken.isEmpty ? nil : savedToken
        let config = VtsConfig(
            host: host,
            port: Int32(port) ?? 8001,
            maxFps: 30,
            pluginName: pluginName,
            pluginDeveloper: devName,
            pluginIcon: nil,
            authToken: token,
            onTokenReceived: { [weak self] newToken in
                self?.savedToken = newToken
            },
            reconnectPolicy: VtsReconnectPolicy()
        )
        let newClient = VtsStreamClient(config: config)
        client?.release()
        client = newClient

        // Observe state
        let stateTask = Task { [weak self] in
            for await state in newClient.state {
                guard let self, !Task.isCancelled else { break }
                self.updateStreamState(state)
            }
        }

        // Connect
        Task { try? await newClient.connect() }

        // Stream data
        let streamTask = Task { [weak self] in
            guard let tracker = self?.tracker else { return }
            for await data in tracker.trackingData {
                guard let self, !Task.isCancelled else { break }
                let params = ParameterConverter.shared.convert(data: data)
                var preview: [(key: String, value: Float)] = []
                for (key, value) in params {
                    if let strKey = key as? String, let numValue = value as? NSNumber {
                        preview.append((key: strKey, value: numValue.floatValue))
                    }
                }
                self.paramPreview = preview
                try? await newClient.sendParameters(parameters: params, faceFound: data.isTracking)
            }
        }

        observeTasks.append(contentsOf: [stateTask, streamTask])
    }

    func disconnect() {
        Task {
            try? await client?.disconnect()
        }
    }

    private func updateStreamState(_ state: StreamState) {
        switch state {
        case is StreamState.Connected:
            streamStateText = "Connected"
            streamStateColor = .green
            isConnected = true
        case is StreamState.Connecting:
            streamStateText = "Connecting..."
            streamStateColor = .yellow
            isConnected = false
        case is StreamState.Authenticating:
            streamStateText = "Authenticating..."
            streamStateColor = .yellow
            isConnected = false
        case is StreamState.Disconnected:
            streamStateText = "Disconnected"
            streamStateColor = .gray
            isConnected = false
        case let r as StreamState.Reconnecting:
            streamStateText = "Reconnecting (\(r.attempt))"
            streamStateColor = .yellow
            isConnected = false
        case let e as StreamState.Error:
            streamStateText = "Error: \(e.message)"
            streamStateColor = .red
            isConnected = false
        default:
            streamStateText = "Unknown"
            streamStateColor = .gray
            isConnected = false
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
            } catch {
                statusText = "Error"
            }
        }
    }

    private func stopTracking() {
        guard let tracker = tracker else { return }
        observeTasks.forEach { $0.cancel() }
        observeTasks = []
        Task {
            do { try await tracker.stop() } catch {}
            isTracking = false
            statusText = "Stopped"
        }
    }

    deinit {
        observeTasks.forEach { $0.cancel() }
        client?.release()
        tracker?.release()
    }
}
