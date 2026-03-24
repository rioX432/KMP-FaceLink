# Quick Start: iOS

Get face tracking running on iOS with SwiftUI.

## 1. Add the Framework

KMP-FaceLink produces an XCFramework. Until SPM publishing is set up, integrate via a local framework build.

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

## Requirements

- iOS 17.0+
- Device with TrueDepth front camera (face tracking)
- `NSCameraUsageDescription` in Info.plist
- `arkit` in `UIRequiredDeviceCapabilities`
- Xcode 16+

## Next Steps

- [API Overview](api-overview.md) for all modules and use cases
- [Android Quick Start](quick-start-android.md) for the Android integration
