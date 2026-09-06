# KMP-FaceLink

[![CI](https://github.com/AnotherBall/KMP-FaceLink/actions/workflows/ci.yml/badge.svg)](https://github.com/AnotherBall/KMP-FaceLink/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/AnotherBall/KMP-FaceLink)](https://github.com/AnotherBall/KMP-FaceLink/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20JVM%20%7C%20Wasm-blue)](https://kotlinlang.org/docs/multiplatform.html)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![API Docs](https://img.shields.io/badge/API%20Docs-Dokka-blue)](https://anotherball.github.io/KMP-FaceLink/)
[![Coverage](https://img.shields.io/badge/Coverage-Report-brightgreen)](https://github.com/AnotherBall/KMP-FaceLink/actions/workflows/ci.yml)

Unified face, hand & body tracking SDK for Kotlin Multiplatform — one API across platforms. The
shared API does not imply equal estimator capability or measured quality on every platform.

## Modules

| Module | Purpose | Stability |
|---|---|---|
| `kmp-facelink` | Core face, hand & body tracking API (Android, iOS, JVM/ONNX, Wasm) with N-euro Predictor smoothing | **Stable API** |
| `kmp-facelink-avatar` | BlendShape → Live2D / VRM parameter mapping (+ Wasm) | **Stable** |
| `kmp-facelink-actions` | Gesture/expression/stream-event triggers, emotion classification, recording | **Stable** |
| `kmp-facelink-voice` | TTS, ASR, multilingual lip sync | **Experimental** |
| `kmp-facelink-llm` | LLM streaming API (cloud + on-device) with hybrid routing | **Experimental** |
| `kmp-facelink-pipeline` | AI avatar pipeline: Voice → LLM → TTS → LipSync → Expression | **Experimental** |
| `kmp-facelink-networking` | Shared UDP socket, time utilities, mDNS service discovery | **Stable** |
| `kmp-facelink-vmc` | VMC Protocol (OSC over UDP) sender/receiver for VRM apps | **Stable** |
| `kmp-facelink-ifm` | iFacialMocap protocol output for VTube Studio | **Stable** |
| `kmp-facelink-stream` | WebSocket streaming server for phone-as-tracker | **Experimental** |
| `kmp-facelink-vts` | VTube Studio Plugin API direct integration | **Stable** |
| `kmp-facelink-interact` | Stream interaction events (Twitch EventSub, YouTube Live Chat) bridged to the action system | **Stable** |
| `kmp-facelink-compose` | Compose Multiplatform `remember*` helpers for tracker lifecycle | **Stable** |

Stability labels describe API intent. They do not claim device qualification, expression accuracy,
runtime performance, or production adoption. `kmp-facelink-capture`, `captureApp`, and
`captureIosApp` are internal measurement tooling and are not published SDK coordinates.
The JVM/OpenSeeFace estimator is mechanically contract-tested but its expression quality and
performance remain experimental and require Tier 3 qualification.

## Tech Stack

| Layer | Technology |
|---|---|
| Shared (KMP) | Kotlin Coroutines / Flow, Ktor Client |
| Android | MediaPipe, CameraX, Jetpack Compose |
| iOS | ARKit, Vision, SwiftUI |
| Interop | SKIE (Swift-Kotlin Flow) |

## Platform Requirements

### Android
- Min SDK 24
- CAMERA permission
- MediaPipe model files in `assets/models/`

### iOS
- iOS 17.0+
- NSCameraUsageDescription in Info.plist
- A device/OS combination where `ARFaceTrackingConfiguration.isSupported` is true at runtime
- ARKit capability in entitlements

### JVM/Desktop (Experimental estimator)
- Run `./scripts/setup-openseeface-models.sh` before a local source build. The script downloads
  OpenSeeFace FP32 models from revision `85aa70fc67582d046e771ea73625182a0d8f7475` and verifies
  both SHA-256 digests. The publication workflow runs the same step before packaging the JAR.
- Runtime startup verifies model digests, tensor names, types, and shapes before opening a camera.
- The real-model zero-input test establishes supply and decoder mechanics only. It does not
  establish expression accuracy, sustained FPS, or product readiness.

### Wasm/JS (Experimental)
- Supported modules: `kmp-facelink`, `kmp-facelink-avatar`
- Kotlin/Wasm target (`wasmJs`) — requires a browser with [WebAssembly GC](https://developer.mozilla.org/en-US/docs/WebAssembly/GC) support (Chrome 119+, Firefox 120+)
- Face (with gaze estimation), hand, and body tracker implementations are available through MediaPipe JS interop (`MediaPipeWasmFaceTracker` / `MediaPipeWasmHandTracker` / `MediaPipeWasmBodyTracker`). Tier 1 compiles and API-checks this target; browser runtime behavior remains separately qualified
- Remaining gaps: holistic tracking is a no-op stub, smoothing (`SmoothingConfig` filters) is not yet implemented on the Wasm trackers, and there is no depth estimation (Android-only)
- Host page must set `globalThis.__faceLinkVision` to the loaded `@mediapipe/tasks-vision` module before starting a tracker
- See [docs/research/kotlin-wasm-web-target.md](docs/research/kotlin-wasm-web-target.md) for roadmap

## Quick Start

- [Android Quick Start](docs/quick-start-android.md)
- [iOS Quick Start](docs/quick-start-ios.md)
- [Integration Guide](docs/quick-start-integration.md)
- [API Overview](docs/api-overview.md)
- [Quality Gates](docs/quality-gates.md)

## Documentation

Full API documentation is available at **[anotherball.github.io/KMP-FaceLink](https://anotherball.github.io/KMP-FaceLink/)**, generated with [Dokka](https://github.com/Kotlin/dokka) and deployed automatically on each release.

### LLM-Friendly Docs

- [`llms.txt`](llms.txt) — Concise project overview optimized for LLM context windows ([llmstxt.org](https://llmstxt.org/) format)
- [`llms-full.txt`](llms-full.txt) — Detailed API reference with all module signatures and usage examples

## [Changelog](CHANGELOG.md)

## Security

For security issues, see [SECURITY.md](SECURITY.md).
