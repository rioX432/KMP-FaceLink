# KMP-FaceLink

Unified face and hand tracking API for Kotlin Multiplatform (KMP).

Wraps platform-specific tracking SDKs — **MediaPipe** (Android) and **ARKit / Vision** (iOS) — into a single shared Kotlin API, providing a consistent stream of facial blend shapes, head transforms, and hand landmarks across platforms.

## Motivation

Building a VTuber or AR app with KMP today means writing separate tracking code for each platform. KMP-FaceLink eliminates this duplication by providing one API that works on both Android and iOS.

## Features

- Unified face & hand tracking API in `commonMain`
- **Android**: MediaPipe Face Landmarker / Hand Landmarker + CameraX
- **iOS**: ARKit face tracking + Vision hand pose estimation
- 52 ARKit-compatible blend shape parameters
- Head position and rotation (6DoF) with 4×4 transform matrix
- 21-joint hand landmarks with gesture classification
- One Euro / EMA adaptive smoothing filters
- Calibration support
- Real-time performance (30–60 fps)

## Quick Start

### Android (Kotlin)

```kotlin
// In your Activity / Fragment
val platformContext = PlatformContext(context, lifecycleOwner)
val tracker = createFaceTracker(platformContext)

lifecycleScope.launch {
    tracker.start()
    tracker.trackingData.collect { data ->
        if (data.isTracking) {
            val jawOpen = data.blendShapes[BlendShape.JAW_OPEN]
            val headYaw = data.headTransform.yaw
            // Drive your avatar...
        }
    }
}

// When done
lifecycle.addObserver(LifecycleEventObserver { _, event ->
    if (event == Lifecycle.Event.ON_DESTROY) tracker.release()
})
```

### iOS (Swift via SKIE)

```swift
import KMPFaceLink

let tracker = FaceTrackerFactory_iosKt.createFaceTracker(
    platformContext: PlatformContext(),
    config: FaceTrackerConfig(
        smoothingConfig: SmoothingConfig.Ema(alpha: 0.4),
        enhancerConfig: BlendShapeEnhancerConfig.Default(),
        enableCalibration: false,
        cameraFacing: .front
    )
)

Task {
    try await tracker.start()
    for await data in tracker.trackingData {
        if data.isTracking {
            // Use data.blendShapes, data.headTransform
        }
    }
}
```

### Hand Tracking

Hand tracking is also available via `createHandTracker()`:

```kotlin
val handTracker = createHandTracker(platformContext)
handTracker.start()
handTracker.trackingData.collect { data ->
    for (hand in data.hands) {
        // hand.landmarks, hand.handedness, hand.gesture
    }
}
```

## Platform Requirements

### Android
- **CAMERA** permission (declared in manifest + requested at runtime)
- MediaPipe model files in `assets/models/` (see sample app)
- Min SDK 24

### iOS
- **NSCameraUsageDescription** in Info.plist
- TrueDepth front camera required for face tracking
- iOS 17.0+
- ARKit capability in entitlements (face tracking)

## Architecture

```
commonMain/
├── api/
│   ├── FaceTracker.kt          # Public face tracking interface
│   └── HandTracker.kt          # Public hand tracking interface
├── model/
│   ├── FaceTrackingData.kt     # Unified face data model
│   ├── HandTrackingData.kt     # Unified hand data model
│   ├── BlendShape.kt           # 52 blend shape definitions
│   └── HandJoint.kt            # 21 hand joint definitions
└── util/                       # Smoothing, calibration

androidMain/
├── MediaPipeFaceTracker.kt     # MediaPipe + CameraX
└── MediaPipeHandTracker.kt

iosMain/
├── ARKitFaceTracker.kt         # ARKit
└── VisionHandTracker.kt        # Vision framework
```

## Documentation

API documentation is available at **[riox432.github.io/KMP-FaceLink](https://riox432.github.io/KMP-FaceLink/)**.

## Tech Stack

- Kotlin Multiplatform (KMP)
- MediaPipe Face / Hand Landmarker (Android)
- ARKit + Vision framework (iOS)
- SKIE (Swift-Kotlin Flow interop)
- Kotlin Coroutines / Flow

## Roadmap

KMP-FaceLink is evolving from a pure tracking library into a modular Mobile VTuber SDK:

| Module | Purpose | Status |
|---|---|---|
| `kmp-facelink` (core) | Face & hand tracking API | Available |
| `kmp-facelink-avatar` | BlendShape → Live2D/VRM parameter conversion | Planned |
| `kmp-facelink-actions` | Gesture/expression → action triggers | Planned |
| `kmp-facelink-effects` | Real-time face effects (TikTok-style filters) | Planned |
| `kmp-facelink-stream` | WebSocket streaming to desktop backends | Future |

See [docs/extension-strategy.md](docs/extension-strategy.md) for the full strategy.

## Status

**Active development** — face tracking and hand tracking are functional on both platforms. Avatar parameter mapping and gesture action modules are next.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

[MIT](LICENSE)
