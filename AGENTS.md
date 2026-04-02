# AGENTS.md

This file provides guidance to AI coding agents working with this repository.

## Project Overview

KMP-FaceLink is a Kotlin Multiplatform SDK for real-time face/hand/body tracking. It wraps MediaPipe (Android) and ARKit/Vision (iOS) into shared Kotlin interfaces, with extension modules for avatar mapping, actions, voice, and LLM integration.

## Modules

| Module | Purpose | Stability |
|--------|---------|-----------|
| `kmp-facelink` | Core tracking (face, hand, body) — Android, iOS, JVM targets | Stable |
| `kmp-facelink-avatar` | Live2D + VRM parameter mapping (BlendShape → avatar params) | Stable |
| `kmp-facelink-actions` | Gesture/expression trigger system with debounce | Stable |
| `kmp-facelink-voice` | ASR (Whisper) / TTS (OpenAI, ElevenLabs, Voicevox) + lip sync | Experimental |
| `kmp-facelink-llm` | LLM streaming API (cloud + on-device) with hybrid routing | Experimental |
| `kmp-facelink-pipeline` | AI avatar pipeline: Voice → LLM → TTS → LipSync → Expression | Experimental |
| `kmp-facelink-networking` | Shared UDP socket and time utilities for protocol modules | Stable |
| `kmp-facelink-vmc` | VMC Protocol (OSC over UDP) sender/receiver for VRM apps | Experimental |
| `kmp-facelink-ifm` | iFacialMocap protocol output (MeowFace replacement) for VTube Studio | Experimental |
| `kmp-facelink-stream` | WebSocket streaming server for phone-as-tracker use case | Experimental |
| `kmp-facelink-vts` | VTube Studio Plugin API direct integration via WebSocket | Experimental |

### Dependency Tree

```
kmp-facelink (core)
  ├── kmp-facelink-avatar    → depends on core
  ├── kmp-facelink-actions   → depends on core
  ├── kmp-facelink-voice     → depends on core
  ├── kmp-facelink-llm       → depends on core
  ├── kmp-facelink-pipeline  → depends on core, voice, llm, actions, avatar
  ├── kmp-facelink-networking → standalone (UDP + time utils)
  ├── kmp-facelink-vmc      → depends on core, networking
  ├── kmp-facelink-ifm      → depends on core, networking
  ├── kmp-facelink-stream   → depends on core
  └── kmp-facelink-vts     → depends on core
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

Modules with platform code: `kmp-facelink` (android/ios/jvm), `kmp-facelink-networking` (android/ios), `kmp-facelink-voice`, `kmp-facelink-llm`
Pure common modules: `kmp-facelink-avatar`, `kmp-facelink-actions`, `kmp-facelink-pipeline`, `kmp-facelink-vts`
Note: `kmp-facelink` has a `jvmMain` source set with no-op tracker stubs for desktop/JVM target support.
Note: `kmp-facelink-llm` includes platform-specific inference engines (llama.cpp on Android, Apple Foundation Models on iOS 26+) for on-device LLM.

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

- **Kotlin 2.2.0** — KMP with `explicitApi()` on all modules
- **Android**: MediaPipe 0.10.33, CameraX 1.5.3, Compose
- **iOS**: ARKit, Vision framework, SwiftUI
- **Networking**: Ktor 3.1.1 (OkHttp on Android, Darwin on iOS)
- **Serialization**: kotlinx-serialization-json 1.8.1
- **Coroutines**: kotlinx-coroutines 1.10.2
- **Interop**: SKIE 0.10.10 (Swift-Kotlin Flow bridging)
- **Build**: AGP 8.8.2, Detekt 1.23.8, Dokka 2.1.0
- **On-device AI**: llama.cpp (Android JNI), Apple Foundation Models (iOS 26+)

### Platform Implementations

**Android (MediaPipe + CameraX)**
- Face: `MediaPipeFaceTracker` (Face Landmarker → ARKit-compatible blend shapes)
- Hand: `MediaPipeHandTracker` (Hand Landmarker)
- Body: `MediaPipeBodyTracker` (Pose Landmarker)
- Camera: `CameraXManager` + `ImageProxyConverter` + `BitmapPool` (bitmap reuse)
- Voice: WhisperCpp native bindings, Android AudioRecord/AudioTrack

**iOS (ARKit + Vision)**
- Face: `ARKitFaceTracker` (ARFaceTrackingConfiguration)
- Hand: `VisionHandTracker` (VNDetectHumanHandPoseRequest + AVCaptureSession)
- Body: `VisionBodyTracker` (VNDetectHumanBodyPose3DRequest + AVCaptureSession)
- Voice: WhisperCpp native bindings, AVFoundation

### Key Public APIs

| Module | Entry Point | Pattern |
|--------|-------------|---------|
| Core | `FaceTracker`, `HandTracker`, `BodyTracker` | `start()`/`stop()`, `trackingData: Flow<T>`, `state: StateFlow<TrackingState>` |
| Core | `HolisticTracker` | `start()`/`stop()`, `trackingData: Flow<HolisticTrackingData>`, configurable modalities |
| Avatar | `Live2DParameterMapper`, `VrmParameterMapper` | `map(FaceTrackingData): Map<String, Float>`, VRM 0.x/1.0 support |
| Actions | `ActionSystem` | `register(ActionBinding)`, `events: Flow<ActionEvent>`, `BuiltInTriggers` |
| Actions | `EmotionClassifier` | `classify(BlendShapeData): EmotionResult`, FACS-based cosine similarity |
| Voice | `VoicePipeline` | `speak(text): Flow<BlendShapes>`, `startListening()`, `transcriptions: Flow<T>` |
| LLM | `LlmClient`, `LlmConfig` | `LlmClient.create(config)`, `chat(history): Flow<String>`, cloud + on-device + hybrid routing |
| LLM | `ModelManager`, `ModelRegistry` | Model download/cache, on-device inference (llama.cpp) |
| LLM | `FoundationModelsProvider`, `FoundationModelsProviderRegistry` | Apple Foundation Models bridge (iOS 26+, no download needed) |
| Pipeline | `AvatarPipeline`, `buildAvatarPipeline {}` | `listen()`, `processText()`, `output: Flow<AvatarPipelineOutput>`, streaming LLM→TTS→LipSync |

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
