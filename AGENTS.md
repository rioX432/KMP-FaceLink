# AGENTS.md

This file provides guidance to AI coding agents working with this repository.

## Project Overview

KMP-FaceLink is a Kotlin Multiplatform SDK for real-time face/hand/body tracking and VTuber applications. It wraps MediaPipe (Android) and ARKit/Vision (iOS) into shared Kotlin interfaces, with extension modules for avatar mapping, actions, effects, Live2D rendering, streaming, and voice.

## Modules

| Module | Purpose | Stability |
|--------|---------|-----------|
| `kmp-facelink` | Core tracking (face, hand, body) | Stable |
| `kmp-facelink-avatar` | Live2D parameter mapping (BlendShape → ParamAngleX etc.) | Stable |
| `kmp-facelink-actions` | Gesture/expression trigger system with debounce | Stable |
| `kmp-facelink-effects` | Real-time face effects engine (TikTok-style filters) | Stable |
| `kmp-facelink-live2d` | Live2D Cubism SDK Native renderer | Experimental |
| `kmp-facelink-stream` | WebSocket streaming (VTubeStudio protocol) | Stable |
| `kmp-facelink-voice` | ASR (Whisper) / TTS (OpenAI, ElevenLabs, Voicevox) + lip sync | Experimental |

### Dependency Tree

```
kmp-facelink (core)
  ├── kmp-facelink-avatar    → depends on core
  │     └── kmp-facelink-live2d  → exports core + avatar
  ├── kmp-facelink-actions   → depends on core
  ├── kmp-facelink-effects   → depends on core
  ├── kmp-facelink-stream    → depends on core
  └── kmp-facelink-voice     → depends on core
```

### Source Set Layout

Each library module follows this pattern:

```
<module>/src/
  ├── commonMain/    # Shared API and logic
  ├── commonTest/    # Unit tests
  ├── androidMain/   # Android platform impl (if needed)
  └── iosMain/       # iOS platform impl (if needed)
```

Modules with platform code: `kmp-facelink`, `kmp-facelink-stream`, `kmp-facelink-voice`
Pure common modules: `kmp-facelink-avatar`, `kmp-facelink-actions`, `kmp-facelink-effects`, `kmp-facelink-live2d`

## Commands

```bash
# Build
./gradlew build                              # Build all modules
./gradlew :<module>:build                    # Build specific module

# Test
./gradlew allTests                           # All tests across all modules
./gradlew :<module>:allTests                 # Tests for a specific module

# Sample apps
./gradlew :androidApp:installDebug           # Install Android sample

# Code quality
./gradlew detekt                             # Static analysis (all modules)
./gradlew ktlintCheck                        # Lint check
./gradlew ktlintFormat                       # Auto-format
```

## Architecture

### Tech Stack

- **Kotlin 2.1.20** — KMP with `explicitApi()` on all modules
- **Android**: MediaPipe 0.10.32, CameraX 1.4.2, Compose
- **iOS**: ARKit, Vision framework, SwiftUI
- **Networking**: Ktor 3.1.1 (OkHttp on Android, Darwin on iOS)
- **Serialization**: kotlinx-serialization-json 1.8.1
- **Coroutines**: kotlinx-coroutines 1.10.2
- **Interop**: SKIE 0.10.9 (Swift-Kotlin Flow bridging)
- **Build**: AGP 8.8.2, Detekt 1.23.8, Dokka 2.1.0

### Platform Implementations

**Android (MediaPipe + CameraX)**
- Face: `MediaPipeFaceTracker` (Face Landmarker → ARKit-compatible blend shapes)
- Hand: `MediaPipeHandTracker` (Hand Landmarker)
- Body: `MediaPipeBodyTracker` (Pose Landmarker)
- Camera: `CameraXManager` + `ImageProxyConverter` + `BitmapPool` (bitmap reuse)
- Stream: Ktor OkHttp engine
- Voice: WhisperCpp native bindings, Android AudioRecord/AudioTrack

**iOS (ARKit + Vision)**
- Face: `ARKitFaceTracker` (ARFaceTrackingConfiguration)
- Hand: `VisionHandTracker` (VNDetectHumanHandPoseRequest + AVCaptureSession)
- Body: `VisionBodyTracker` (VNDetectHumanBodyPoseRequest + AVCaptureSession)
- Stream: Ktor Darwin engine
- Voice: WhisperCpp native bindings, AVFoundation

### Key Public APIs

| Module | Entry Point | Pattern |
|--------|-------------|---------|
| Core | `FaceTracker`, `HandTracker`, `BodyTracker` | `start()`/`stop()`, `trackingData: Flow<T>`, `state: StateFlow<TrackingState>` |
| Core | `HolisticTracker` | `start()`/`stop()`, `trackingData: Flow<HolisticTrackingData>`, configurable modalities |
| Avatar | `Live2DParameterMapper` | `map(FaceTrackingData): Map<String, Float>` + `PerfectSyncMappings` |
| Actions | `ActionSystem` | `register(ActionBinding)`, `events: Flow<ActionEvent>`, `BuiltInTriggers` |
| Effects | `EffectEngine` | `addEffect(Effect)`, `processFace(data): EffectOutput` |
| Live2D | `Live2DRenderer` | `initialize(modelInfo)`, `updateParameters(params)` (`@ExperimentalFaceLinkApi`) |
| Stream | `VtsStreamClient` | `connect()`/`disconnect()`, `sendParameters(params, faceFound)` |
| Voice | `VoicePipeline` | `speak(text): Flow<BlendShapes>`, `startListening()`, `transcriptions: Flow<T>` |

### Key Design Decisions

- **BlendShape naming**: ARKit conventions (52 parameters). MediaPipe landmarks are mapped to ARKit-compatible names
- **Data streaming**: Always `Flow<T>`, never callbacks
- **Thread safety**: `PlatformLock` (ReentrantLock on Android, NSLock on iOS) + `AtomicInt`
- **Head transform**: Right-handed coordinate system
- **Performance target**: 30-60fps real-time tracking with smoothing filters

## Development Guidelines

### Code Rules

- **`explicitApi()` enforced** — all public symbols need explicit visibility modifiers
- Public API in `commonMain` only — keep it minimal and clean
- Platform implementations are `internal`, exposed only through shared interfaces
- Use `Flow<T>` for streaming data (not callbacks)
- `expect`/`actual` declarations require `-Xexpect-actual-classes` compiler flag

### Detekt Rules

Detekt is strictly enforced. Key rules:

| Rule | Config |
|------|--------|
| **ImportOrdering** | Layout: `*,java.*,javax.*,kotlin.*,^` — `kotlinx.*` belongs to `*` group, must come BEFORE `java.*` |
| **LongMethod** | Max 60 lines |
| **MagicNumber** | Strict (ignores -1, 0, 1, 2, 16, 1000 only) |
| **MaxLineLength** | 150 characters |
| **TooManyFunctions** | Max 15 per class/file |
| **ReturnCount** | Max 5 |

### Naming Conventions

- BlendShape parameters: ARKit names (e.g., `eyeBlinkLeft`, `jawOpen`, `mouthSmileLeft`)
- All 52 ARKit blend shapes are the canonical set
- Tracking data classes: `FaceTrackingData`, `HandTrackingData`, `BodyTrackingData`
- Factory pattern: `FaceTrackerFactory.create(config, context)`

### Sample Apps

- **Android** (`androidApp/`): Compose UI with camera preview, landmark overlay, avatar tabs
- **iOS** (`iosApp/`): SwiftUI, XcodeGen from `project.yml`, iOS 17.0+, requires ARKit capability

## Language

All generated content must be in English: code, comments, commit messages, PR titles/descriptions, documentation, and TASKS.md entries. Communicate with users in their preferred language.
