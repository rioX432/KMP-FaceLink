# KMP-FaceLink

[![CI](https://github.com/AnotherBall/KMP-FaceLink/actions/workflows/ci.yml/badge.svg)](https://github.com/AnotherBall/KMP-FaceLink/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.anotherball/kmp-facelink)](https://central.sonatype.com/namespace/com.anotherball)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20JVM%20%7C%20Wasm-blue)](https://kotlinlang.org/docs/multiplatform.html)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![API Docs](https://img.shields.io/badge/API%20Docs-Dokka-blue)](https://anotherball.github.io/KMP-FaceLink/)
[![Coverage](https://img.shields.io/badge/Coverage-Report-brightgreen)](https://github.com/AnotherBall/KMP-FaceLink/actions/workflows/ci.yml)

Unified face, hand & body tracking SDK for Kotlin Multiplatform — one API, both platforms.

## Modules

| Module | Purpose | Stability |
|---|---|---|
| `kmp-facelink` | Core face, hand & body tracking API (Android, iOS, JVM/ONNX, Wasm) with N-euro Predictor smoothing | **Stable** |
| `kmp-facelink-avatar` | BlendShape → Live2D / VRM parameter mapping (+ Wasm) | **Stable** |
| `kmp-facelink-actions` | Gesture/expression triggers, emotion classification, recording (+ Wasm) | **Stable** |
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
- TrueDepth front camera (face tracking)
- ARKit capability in entitlements

### Wasm/JS (Experimental)
- Supported modules: `kmp-facelink`, `kmp-facelink-avatar`, `kmp-facelink-actions`
- Kotlin/Wasm target (`wasmJs`) — requires a browser with [WebAssembly GC](https://developer.mozilla.org/en-US/docs/WebAssembly/GC) support (Chrome 119+, Firefox 120+)
- Face tracking functional via MediaPipe JS interop (with gaze estimation); hand/body tracking are no-op stubs; avatar mapping and action triggers are fully functional
- See [docs/research/kotlin-wasm-web-target.md](docs/research/kotlin-wasm-web-target.md) for roadmap

## Quick Start

- [Android Quick Start](docs/quick-start-android.md)
- [iOS Quick Start](docs/quick-start-ios.md)
- [Integration Guide](docs/quick-start-integration.md)
- [API Overview](docs/api-overview.md)

## Documentation

Full API documentation is available at **[anotherball.github.io/KMP-FaceLink](https://anotherball.github.io/KMP-FaceLink/)**, generated with [Dokka](https://github.com/Kotlin/dokka) and deployed automatically on each release.

### LLM-Friendly Docs

- [`llms.txt`](llms.txt) — Concise project overview optimized for LLM context windows ([llmstxt.org](https://llmstxt.org/) format)
- [`llms-full.txt`](llms-full.txt) — Detailed API reference with all module signatures and usage examples

## [Changelog](CHANGELOG.md)

## Security

For security issues, see [SECURITY.md](SECURITY.md).
