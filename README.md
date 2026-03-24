# KMP-FaceLink

Unified face, hand & body tracking SDK for Kotlin Multiplatform — one API, both platforms.

## Modules

| Module | Purpose | Stability |
|---|---|---|
| `kmp-facelink` | Core face, hand & body tracking API (Android, iOS, JVM) | **Stable** |
| `kmp-facelink-avatar` | BlendShape → Live2D / VRM parameter mapping | **Stable** |
| `kmp-facelink-actions` | Gesture/expression triggers, emotion classification, recording | **Stable** |
| `kmp-facelink-voice` | TTS, ASR, multilingual lip sync | **Mostly stable** |
| `kmp-facelink-llm` | LLM streaming API (cloud + on-device) with hybrid routing | **Experimental** |
| `kmp-facelink-pipeline` | AI avatar pipeline: Voice → LLM → TTS → LipSync → Expression | **Experimental** |
| `kmp-facelink-networking` | Shared UDP socket, time utilities, mDNS service discovery | **Stable** |
| `kmp-facelink-vmc` | VMC Protocol (OSC over UDP) sender/receiver for VRM apps | **Experimental** |
| `kmp-facelink-ifm` | iFacialMocap protocol output for VTube Studio | **Experimental** |
| `kmp-facelink-stream` | WebSocket streaming server for phone-as-tracker | **Experimental** |
| `kmp-facelink-vts` | VTube Studio Plugin API direct integration | **Experimental** |

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

## [Changelog](CHANGELOG.md)
