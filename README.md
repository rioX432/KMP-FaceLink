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
