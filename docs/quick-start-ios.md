# Quick Start: iOS

Get face tracking running on iOS with SwiftUI.

## 1. Add the Framework

KMP-FaceLink produces an XCFramework distributed via Swift Package Manager (SPM). The `Package.swift` at the repository root references binary XCFrameworks attached to each GitHub release. Until the first release is published, integrate via a local framework build.

### Option A: XcodeGen (recommended for sample projects)

Add to your `project.yml`:

```yaml
options:
  deploymentTarget:
    iOS: "17.0"

settings:
  base:
    FRAMEWORK_SEARCH_PATHS:
      - $(inherited)
      - $(SRCROOT)/../kmp-facelink/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)

targets:
  YourApp:
    type: application
    platform: iOS
    dependencies:
      - framework: ../kmp-facelink/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)/KMPFaceLink.framework
        embed: false
    preBuildScripts:
      - name: Build KMP Framework
        script: |
          cd "$SRCROOT/.."
          ./gradlew :kmp-facelink:embedAndSignAppleFrameworkForXcode
        basedOnDependencyAnalysis: false
    info:
      properties:
        NSCameraUsageDescription: "Face tracking requires camera access"
        UIRequiredDeviceCapabilities:
          - arkit
          - front-facing-camera
```

Then run:

```bash
xcodegen generate
```

### Option B: Manual Xcode setup

1. Build the framework:
   ```bash
   ./gradlew :kmp-facelink:assembleKMPFaceLinkReleaseXCFramework
   ```
2. Drag the XCFramework from `kmp-facelink/build/XCFrameworks/release/` into your Xcode project.
3. Add `NSCameraUsageDescription` to `Info.plist`.
4. Add `arkit` to `UIRequiredDeviceCapabilities`.

## 2. Start Face Tracking (SwiftUI)

```swift
import SwiftUI
import KMPFaceLink

struct FaceTrackingView: View {
    @State private var jawOpen: Float = 0.0
    @State private var isTracking = false

    private let tracker = FaceTrackerFactoryKt.createFaceTracker(
        platformContext: PlatformContext(),
        config: FaceTrackerConfig()
    )

    var body: some View {
        VStack {
            Text("Jaw Open: \(jawOpen, specifier: "%.2f")")
                .font(.largeTitle)
            Text(isTracking ? "Tracking" : "No face detected")
                .foregroundColor(isTracking ? .green : .red)
        }
        .task {
            try? await tracker.start()
            for await data in tracker.trackingData {
                isTracking = data.isTracking
                if data.isTracking {
                    jawOpen = data.blendShapes[BlendShape.jawOpen] ?? 0
                }
            }
        }
    }
}
```

> **SKIE** bridges Kotlin `Flow` to Swift `AsyncSequence`, enabling the `for await` syntax. Functions prefixed with `init` in Kotlin are renamed with a `do` prefix in Kotlin/Native (e.g., `initKoin` becomes `doInitKoin`), but SKIE generates its own async extension without the prefix.

## 3. Hand Tracking

iOS hand tracking uses the Vision framework (`VNDetectHumanHandPoseRequest`) instead of ARKit:

```swift
import KMPFaceLink

let handTracker = HandTrackerFactoryKt.createHandTracker(
    platformContext: PlatformContext(),
    config: HandTrackerConfig()
)

// In a SwiftUI .task:
try? await handTracker.start()
for await data in handTracker.trackingData {
    for hand in data.hands {
        print("Gesture: \(hand.gesture), Hand: \(hand.handedness)")
    }
}
```

## 4. Holistic Tracking (Face + Hands + Body)

```swift
let holisticTracker = HolisticTrackerFactoryKt.createHolisticTracker(
    platformContext: PlatformContext(),
    config: HolisticTrackerConfig(enableFace: true, enableHands: true, enableBody: true)
)

try? await holisticTracker.start()
for await data in holisticTracker.trackingData {
    if let face = data.face { /* ARKit blend shapes */ }
    if let hands = data.hands { /* Vision hand landmarks */ }
    if let body = data.body { /* Vision body pose */ }
}
```

## Platform Details

| Feature | Implementation | Notes |
|---------|---------------|-------|
| Face tracking | ARKit (`ARFaceTrackingConfiguration`) | TrueDepth front camera only |
| Hand tracking | Vision (`VNDetectHumanHandPoseRequest`) | Front or back camera via AVCaptureSession |
| Body tracking | Vision (`VNDetectHumanBodyPoseRequest`) | Front or back camera via AVCaptureSession |
| Blend shapes | 52 ARKit parameters natively | No conversion needed |

## iOS Lifecycle Management

Correct lifecycle management is critical on iOS. Violating the required sequence causes
the camera to stay locked (blocking other apps), ARKit/AVCaptureSession sessions to leak,
and orphaned Swift Tasks to continue collecting data from a stopped tracker.

### Required sequence: Task cancel -> stop() -> release()

When you stop or tear down a tracker, you **must** follow this order:

1. **Cancel the Flow-collection Task** — stops the `for await` loop consuming `trackingData` / `state`.
2. **Call `tracker.stop()`** — tears down the ARKit session or AVCaptureSession and releases the camera.
3. **Call `tracker.release()` in `deinit`** — frees all remaining native resources.

```swift
@MainActor
class TrackingViewModel: ObservableObject {
    private var tracker: FaceTracker?
    private var observeTask: Task<Void, Never>?

    init() {
        let config = FaceTrackerConfig(
            smoothingConfig: SmoothingConfig.Ema(alpha: 0.4),
            enhancerConfig: nil,
            calibrationConfig: CalibrationConfig(),
            cameraFacing: .front
        )
        tracker = FaceTrackerFactory_iosKt.createFaceTracker(
            platformContext: PlatformContext(),
            config: config
        )
    }

    func startTracking() {
        guard let tracker else { return }
        Task {
            do {
                // Start the collection Task BEFORE calling start() so no
                // frames are missed.
                observeTask = Task { [weak self] in
                    for await data in tracker.trackingData {
                        guard !Task.isCancelled else { break }
                        // Update UI (already on @MainActor)
                        self?.handleData(data)
                    }
                }
                try await tracker.start()
            } catch {
                print("Start failed: \(error)")
            }
        }
    }

    func stopTracking() {
        // 1. Cancel the collection Task FIRST
        observeTask?.cancel()
        observeTask = nil

        // 2. Then stop the tracker
        guard let tracker else { return }
        Task {
            do {
                try await tracker.stop()
            } catch {
                // Best-effort stop
            }
        }
    }

    // 3. Release in deinit
    deinit {
        observeTask?.cancel()
        tracker?.release()
    }

    private func handleData(_ data: FaceTrackingData) {
        // process tracking data
    }
}
```

### Why this order matters

| Step skipped | Consequence |
|---|---|
| Cancel Task before `stop()` | The `for await` loop keeps running against a stopped tracker. The Task becomes orphaned and is never deallocated. |
| Call `stop()` | Camera stays locked — other apps (and even your own app on next launch) cannot access the camera. ARKit/AVCaptureSession session leaks. |
| Call `release()` in `deinit` | Native resources (buffers, pipeline objects) are never freed. Repeated create/destroy cycles accumulate leaked memory. |

### Threading caveat

The ViewModel is marked `@MainActor`, but **tracker callbacks are NOT dispatched on the
main thread**. `trackingData` and `state` emit on platform-specific background threads
(ARKit delegate thread for face, AVCaptureSession delegate thread for hand/body).

When you use SKIE's `for await` inside a `Task` created on a `@MainActor` context, Swift
automatically hops back to the main actor for each iteration. If you collect from a
non-`@MainActor` context, you must dispatch to the main thread yourself before updating UI.

### Common mistakes

**1. Forgetting to cancel Task before stop()**

```swift
// WRONG: Task keeps collecting from a stopped tracker
func stopTracking() {
    Task { try await tracker.stop() }    // Task still running!
    observeTask?.cancel()                // Too late — stop() already tore down the session
}

// CORRECT: Cancel first, then stop
func stopTracking() {
    observeTask?.cancel()
    observeTask = nil
    Task { try await tracker.stop() }
}
```

**2. Not calling release() in deinit**

```swift
// WRONG: resources leak when ViewModel is deallocated
deinit {
    observeTask?.cancel()
    // tracker.release() is missing!
}

// CORRECT: always release
deinit {
    observeTask?.cancel()
    tracker?.release()
}
```

**3. Assuming callbacks arrive on the main thread**

```swift
// WRONG in a non-@MainActor context: UI update on background thread
let task = Task.detached {
    for await data in tracker.trackingData {
        self.label.text = "\(data.blendShapes)"  // UIKit crash
    }
}

// CORRECT: dispatch to main
let task = Task.detached { [weak self] in
    for await data in tracker.trackingData {
        await MainActor.run {
            self?.label.text = "\(data.blendShapes)"
        }
    }
}
```

**4. Using `.task { }` without cleanup**

The SwiftUI `.task` modifier cancels automatically when the view disappears, but it
does NOT call `stop()` or `release()`. You must handle that explicitly:

```swift
// WRONG: camera stays running after view disappears
.task {
    try? await tracker.start()
    for await data in tracker.trackingData { /* ... */ }
}

// CORRECT: use onDisappear for cleanup
.task {
    try? await tracker.start()
    for await data in tracker.trackingData { /* ... */ }
}
.onDisappear {
    Task { try? await tracker.stop() }
}
```

For anything beyond a simple single-screen demo, prefer the ViewModel pattern shown
above — it gives you explicit control over the full lifecycle.

## Requirements

- iOS 17.0+
- Device with TrueDepth front camera (face tracking)
- `NSCameraUsageDescription` in Info.plist
- `arkit` in `UIRequiredDeviceCapabilities`
- Xcode 16+

## Next Steps

- [API Overview](api-overview.md) for all modules and use cases
- [Android Quick Start](quick-start-android.md) for the Android integration
- [Troubleshooting](troubleshooting.md) for common integration issues
