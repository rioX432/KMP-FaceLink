# KMP-FaceLink

Unified face, hand & body tracking SDK for Kotlin Multiplatform — one API, both platforms.

## Modules

| Module | Purpose | Stability |
|---|---|---|
| `kmp-facelink` | Core face, hand & body tracking API | **Stable** |
| `kmp-facelink-avatar` | BlendShape → Live2D parameter mapping | **Stable** |
| `kmp-facelink-actions` | Gesture/expression triggers, emotion, recording | **Stable** |
| `kmp-facelink-voice` | TTS, ASR, lip sync | **Mostly stable** |
| `kmp-facelink-llm` | LLM streaming API (OpenAI, Anthropic, Gemini) | **Experimental** |

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
