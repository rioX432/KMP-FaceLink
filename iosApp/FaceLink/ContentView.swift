import SwiftUI
import ARKit
import KMPFaceLink
import SampleShared

enum TrackingMode: String, CaseIterable {
    case home = "Home"
    case face = "Face"
    case hand = "Hand"
    case holistic = "Holistic"
    case actions = "Actions"
    case voice = "Voice"
}

struct ContentView: View {
    @State private var selectedMode: TrackingMode = .home

    var body: some View {
        VStack(spacing: 0) {
            if selectedMode != .home {
                // Scrollable mode picker (hidden on home)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(TrackingMode.allCases.filter { $0 != .home }, id: \.self) { mode in
                            ModeChip(
                                title: mode.rawValue,
                                isSelected: selectedMode == mode
                            ) {
                                selectedMode = mode
                            }
                        }
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                }
                .background(Color(.systemBackground))
            }

            // Content
            switch selectedMode {
            case .home:
                HomeView(onModeSelect: { selectedMode = $0 })
            case .face:
                CMPFaceTrackingView()
            case .hand:
                CMPHandTrackingView()
            case .holistic:
                HolisticTrackingView()
            case .actions:
                ActionsTrackingView()
            case .voice:
                VoiceTrackingView()
            }
        }
    }
}

struct ModeChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(.medium))
                .foregroundStyle(.primary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    isSelected ? Color.blue.opacity(0.6) : Color(.tertiarySystemFill),
                    in: Capsule()
                )
                .frame(minHeight: 44)
        }
    }
}

// MARK: - CMP Hand Tracking (Compose Multiplatform)

/// Compose Multiplatform hand tracking screen hosted via ComposeUIViewController.
struct CMPHandTrackingView: View {
    @StateObject private var viewModel = CMPHandTrackingViewModel()

    var body: some View {
        ComposeViewControllerWrapper {
            HandTrackingViewControllerKt.HandTrackingViewController(
                handTracker: viewModel.tracker,
                trackingDataState: viewModel.dataFlow,
                onModeChange: { _ in
                    // Mode change is handled by SwiftUI's own mode picker above
                },
                onStartClick: {
                    viewModel.startTracking()
                },
                onStopClick: {
                    viewModel.stopTracking()
                }
            )
        }
        .ignoresSafeArea()
    }
}

@MainActor
class CMPHandTrackingViewModel: ObservableObject {
    let tracker: HandTracker
    let dataFlow: Kotlinx_coroutines_coreMutableStateFlow

    private var observeTask: Task<Void, Never>?

    init() {
        let config = HandTrackerConfig(
            smoothingConfig: SmoothingConfig.Ema(alpha: 0.4),
            maxHands: 2,
            enableGestureRecognition: true,
            cameraFacing: .front
        )
        tracker = HandTrackerFactory_iosKt.createHandTracker(
            platformContext: PlatformContext(),
            config: config
        )
        dataFlow = FlowUtilsKt.createHandTrackingDataStateFlow()
    }

    func startTracking() {
        Task {
            do {
                observeTask = Task { [weak self] in
                    guard let self else { return }
                    for await data in self.tracker.trackingData {
                        guard !Task.isCancelled else { break }
                        self.dataFlow.value = data
                    }
                }
                try await tracker.start()
            } catch {
                print("Failed to start hand tracking: \(error)")
            }
        }
    }

    func stopTracking() {
        observeTask?.cancel()
        observeTask = nil
        Task {
            do {
                try await tracker.stop()
            } catch {
                print("Failed to stop hand tracking: \(error)")
            }
        }
    }

    deinit {
        observeTask?.cancel()
        tracker.release()
    }
}

// MARK: - CMP Face Tracking (Compose Multiplatform)

/// Compose Multiplatform face tracking screen hosted via ComposeUIViewController.
struct CMPFaceTrackingView: View {
    @StateObject private var viewModel = CMPFaceTrackingViewModel()

    var body: some View {
        ComposeViewControllerWrapper {
            FaceTrackingViewControllerKt.FaceTrackingViewController(
                faceTracker: viewModel.tracker,
                trackingDataState: viewModel.dataFlow,
                onModeChange: { _ in
                    // Mode change is handled by SwiftUI's own mode picker above
                },
                onStartClick: {
                    viewModel.startTracking()
                },
                onStopClick: {
                    viewModel.stopTracking()
                }
            )
        }
        .ignoresSafeArea()
    }
}

@MainActor
class CMPFaceTrackingViewModel: ObservableObject {
    let tracker: FaceTracker
    let dataFlow: Kotlinx_coroutines_coreMutableStateFlow

    private var observeTask: Task<Void, Never>?

    init() {
        let companion = BlendShapeEnhancerConfig.Companion.shared
        let config = FaceTrackerConfig(
            smoothingConfig: SmoothingConfig.Ema(alpha: 0.4),
            enhancerConfig: BlendShapeEnhancerConfig.Default(
                sensitivityOverrides: companion.defaultSensitivityMap,
                deadZoneOverrides: companion.defaultDeadZoneMap,
                geometricBlendWeight: 0.7
            ),
            calibrationConfig: CalibrationConfig(),
            cameraFacing: .front
        )
        tracker = FaceTrackerFactory_iosKt.createFaceTracker(
            platformContext: PlatformContext(),
            config: config
        )
        dataFlow = FlowUtilsKt.createFaceTrackingDataStateFlow()
    }

    func startTracking() {
        Task {
            do {
                observeTask = Task { [weak self] in
                    guard let self else { return }
                    for await data in self.tracker.trackingData {
                        guard !Task.isCancelled else { break }
                        self.dataFlow.value = data
                    }
                }
                try await tracker.start()
            } catch {
                print("Failed to start tracking: \(error)")
            }
        }
    }

    func stopTracking() {
        observeTask?.cancel()
        observeTask = nil
        Task {
            do {
                try await tracker.stop()
            } catch {
                print("Failed to stop face tracking: \(error)")
            }
        }
    }

    deinit {
        observeTask?.cancel()
        tracker.release()
    }
}

#Preview {
    ContentView()
}
