import SwiftUI
import ARKit
import SampleShared

enum TrackingMode: String, CaseIterable {
    case home = "Home"
    case face = "Face"
    case hand = "Hand"
    case interact = "Interact"
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
            case .interact:
                CMPInteractDemoView()
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
                .dynamicTypeSize(...DynamicTypeSize.accessibility1)
                .foregroundStyle(.primary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    isSelected ? Color.blue.opacity(0.6) : Color(.tertiarySystemFill),
                    in: Capsule()
                )
                .frame(minHeight: 44)
        }
        .accessibilityLabel("\(title) mode")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

// MARK: - CMP Hand Tracking (Compose Multiplatform)

/// Compose Multiplatform hand tracking screen hosted via ComposeUIViewController.
struct CMPHandTrackingView: View {
    @StateObject private var viewModel = CMPHandTrackingViewModel()

    var body: some View {
        ComposeViewControllerWrapper {
            HandTrackingViewControllerKt.HandTrackingViewController(
                handTracker: viewModel.session.tracker,
                trackingDataState: viewModel.session.trackingDataState,
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
    let session = IosHandSampleSession()

    func startTracking() {
        Task {
            do {
                try await session.start()
            } catch {
                print("Failed to start hand tracking: \(error)")
            }
        }
    }

    func stopTracking() {
        Task {
            do {
                try await session.stop()
            } catch {
                print("Failed to stop hand tracking: \(error)")
            }
        }
    }

    deinit {
        session.release()
    }
}

// MARK: - CMP Face Tracking (Compose Multiplatform)

/// Compose Multiplatform face tracking screen hosted via ComposeUIViewController.
struct CMPFaceTrackingView: View {
    @StateObject private var viewModel = CMPFaceTrackingViewModel()

    var body: some View {
        ComposeViewControllerWrapper {
            FaceTrackingViewControllerKt.FaceTrackingViewController(
                faceTracker: viewModel.session.tracker,
                trackingDataState: viewModel.session.trackingDataState,
                initialSmoothingConfig: viewModel.session.initialSmoothingConfig,
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
    let session = IosFaceSampleSession()

    func startTracking() {
        Task {
            do {
                try await session.start()
            } catch {
                print("Failed to start tracking: \(error)")
            }
        }
    }

    func stopTracking() {
        Task {
            do {
                try await session.stop()
            } catch {
                print("Failed to stop face tracking: \(error)")
            }
        }
    }

    deinit {
        session.release()
    }
}

// MARK: - CMP Interact Demo (Compose Multiplatform)

/// Compose Multiplatform interact demo screen hosted via ComposeUIViewController.
struct CMPInteractDemoView: View {
    var body: some View {
        ComposeViewControllerWrapper {
            InteractDemoViewControllerKt.InteractDemoViewController()
        }
        .ignoresSafeArea()
    }
}

#Preview {
    ContentView()
}
