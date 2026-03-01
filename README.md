<div align="center">

# KMP-FaceLink

**Unified face & hand tracking SDK for Kotlin Multiplatform — one API, both platforms.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-green.svg)]()
[![CI](https://github.com/rioX432/KMP-FaceLink/actions/workflows/ci.yml/badge.svg)](https://github.com/rioX432/KMP-FaceLink/actions/workflows/ci.yml)
[![API Docs](https://img.shields.io/badge/API%20Docs-GitHub%20Pages-orange.svg)](https://riox432.github.io/KMP-FaceLink/)

</div>

## Why KMP-FaceLink?

Building face/hand tracking into a mobile app today is harder than it should be:

- **Platform fragmentation** — MediaPipe on Android, ARKit + Vision on iOS. Two completely different APIs, data models, and coordinate systems.
- **VTuber SDK complexity** — Driving a Live2D avatar requires blend shape mapping, smoothing, calibration, gesture detection, and streaming — all glued together manually.
- **No unified mobile tracking API** — Existing solutions are either platform-locked or require bridging through web views. There's no native KMP option.

KMP-FaceLink solves this with a single Kotlin API that wraps platform-specific SDKs and provides a modular toolkit for tracking, avatar control, effects, streaming, and voice.

## Features

- **Face tracking** — 52 ARKit-compatible blend shapes, head position & rotation (6DoF)
- **Hand tracking** — 21-joint landmarks with gesture classification
- **Holistic tracking** — simultaneous face + hand tracking
- **Smoothing** — One Euro / EMA adaptive filters with calibration support
- **Actions** — gesture triggers, emotion recognition, recording & playback
- **Effects** — TikTok-style real-time face effects engine
- **Avatar** — Live2D parameter mapping + native Cubism SDK rendering
- **Stream** — VTubeStudio-compatible WebSocket streaming
- **Voice** — TTS (Voicevox / OpenAI / ElevenLabs), ASR, lip sync
- **LLM** — streaming chat API for OpenAI, Anthropic, Gemini with conversation management
- **Real-time performance** — 30–60 fps on both platforms

## Who Is This For?

- **VTuber app developers** — ship a mobile VTuber client without writing platform-specific tracking code twice.
- **AR / face filter developers** — build Snapchat/TikTok-style face effects with a shared codebase.
- **KMP enthusiasts** — if you need cross-platform camera-based tracking in a Kotlin Multiplatform project, this is the library.

## Modules

| Module | Purpose | Stability |
|---|---|---|
| `kmp-facelink` | Core face & hand tracking API | **Stable** |
| `kmp-facelink-avatar` | BlendShape → Live2D parameter mapping | **Stable** |
| `kmp-facelink-actions` | Gesture/expression triggers, emotion, recording | **Stable** |
| `kmp-facelink-effects` | Real-time face effects engine | **Mostly stable** |
| `kmp-facelink-live2d` | Live2D Cubism SDK rendering | **Experimental** |
| `kmp-facelink-stream` | WebSocket streaming (VTubeStudio protocol) | **Stable** |
| `kmp-facelink-voice` | TTS, ASR, lip sync | **Mostly stable** |
| `kmp-facelink-rive` | Rive avatar integration | **Experimental** |
| `kmp-facelink-llm` | LLM streaming API (OpenAI, Anthropic, Gemini) | **Experimental** |

See [docs/extension-strategy.md](docs/extension-strategy.md) for the full module strategy.

## Tech Stack

| Layer | Technology |
|---|---|
| Shared (KMP) | Kotlin Coroutines / Flow, Ktor Client |
| Android | MediaPipe, CameraX, Jetpack Compose |
| iOS | ARKit, Vision, SwiftUI |
| Interop | SKIE (Swift-Kotlin Flow) |
| Avatar | Live2D Cubism SDK Native |

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

## Architecture

```
kmp-facelink/              Core face & hand tracking API
kmp-facelink-avatar/       BlendShape → Live2D parameter mapping
kmp-facelink-actions/      Gesture triggers, emotion recognition, recording
kmp-facelink-effects/      Real-time face effects engine
kmp-facelink-live2d/       Live2D Cubism SDK rendering
kmp-facelink-stream/       WebSocket streaming (VTubeStudio protocol)
kmp-facelink-voice/        TTS, ASR, lip sync
kmp-facelink-rive/         Rive avatar integration
kmp-facelink-llm/          LLM streaming API (OpenAI, Anthropic, Gemini)
```

## API Stability

KMP-FaceLink follows [Semantic Versioning](https://semver.org/). Each module has a stability tier:

| Module | Stability | Notes |
|---|---|---|
| `kmp-facelink` (core) | **Stable** | Breaking changes only in major versions |
| `kmp-facelink-avatar` | **Stable** | Breaking changes only in major versions |
| `kmp-facelink-actions` | **Stable** | Breaking changes only in major versions |
| `kmp-facelink-effects` | **Mostly stable** | Some APIs marked `@ExperimentalFaceLinkApi` |
| `kmp-facelink-live2d` | **Experimental** | All APIs marked `@ExperimentalFaceLinkApi` |
| `kmp-facelink-stream` | **Stable** | Breaking changes only in major versions |
| `kmp-facelink-voice` | **Mostly stable** | Some APIs marked `@ExperimentalFaceLinkApi` |
| `kmp-facelink-rive` | **Experimental** | All APIs marked `@ExperimentalFaceLinkApi` |
| `kmp-facelink-llm` | **Experimental** | All APIs marked `@ExperimentalFaceLinkApi` |

APIs annotated with `@ExperimentalFaceLinkApi` may change in minor releases without a deprecation cycle. To use them, opt in with:

```kotlin
// Per-usage
@OptIn(ExperimentalFaceLinkApi::class)

// Or module-wide in build.gradle.kts
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=io.github.kmpfacelink.ExperimentalFaceLinkApi")
    }
}
```

All library modules enforce `explicitApi()` mode — every public declaration requires an explicit visibility modifier.

## Documentation

API documentation is available at **[riox432.github.io/KMP-FaceLink](https://riox432.github.io/KMP-FaceLink/)**.

See [CHANGELOG.md](CHANGELOG.md) for release history.

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

## Status

All 9 modules implemented. Sample apps with 8 demo modes on both platforms.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

[MIT](LICENSE)

---

Built by [@rioX432](https://github.com/rioX432)
