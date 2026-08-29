# AGENTS.md

This file provides guidance to AI coding agents working with this repository.

## Project Overview

KMP-FaceLink is a Kotlin Multiplatform SDK for real-time face/hand/body tracking. It wraps MediaPipe (Android) and ARKit/Vision (iOS) into shared Kotlin interfaces, with extension modules for avatar mapping, actions, voice, and LLM integration.

## Modules

| Module | Purpose | Stability |
|--------|---------|-----------|
| `kmp-facelink` | Core tracking (face, hand, body) — Android, iOS, JVM targets | Stable |
| `kmp-facelink-avatar` | Live2D + VRM parameter mapping (BlendShape → avatar params) | Stable |
| `kmp-facelink-actions` | Gesture/expression/stream-event trigger system with debounce | Stable |
| `kmp-facelink-voice` | ASR (Whisper) / TTS (OpenAI, ElevenLabs, Voicevox) + lip sync | Experimental |
| `kmp-facelink-llm` | LLM streaming API (cloud + on-device) with hybrid routing | Experimental |
| `kmp-facelink-pipeline` | AI avatar pipeline: Voice → LLM → TTS → LipSync → Expression | Experimental |
| `kmp-facelink-networking` | Shared UDP socket and time utilities for protocol modules | Stable |
| `kmp-facelink-vmc` | VMC Protocol (OSC over UDP) sender/receiver for VRM apps | Stable |
| `kmp-facelink-ifm` | iFacialMocap protocol output (MeowFace replacement) for VTube Studio | Stable |
| `kmp-facelink-stream` | WebSocket streaming server for phone-as-tracker use case | Experimental |
| `kmp-facelink-vts` | VTube Studio Plugin API direct integration via WebSocket | Stable |
| `kmp-facelink-interact` | Stream interaction events for Twitch/YouTube and mock sources | Stable |
| `kmp-facelink-compose` | Compose Multiplatform tracker lifecycle helpers | Stable |
| `kmp-facelink-capture` | Internal guided-capture and evaluation harness; not published | Experimental |

The stability column describes source/API intent only. It is not evidence of device qualification,
expression accuracy, runtime performance, or adoption in an AnotherBall production application.

### Dependency Tree

```
kmp-facelink (core)
  ├── kmp-facelink-avatar       → depends on core
  ├── kmp-facelink-actions      → depends on core, interact
  ├── kmp-facelink-voice        → depends on core, networking
  ├── kmp-facelink-llm          → depends on core, networking
  ├── kmp-facelink-pipeline     → depends on core, voice, llm, actions, avatar
  ├── kmp-facelink-networking   → standalone (UDP + time utils)
  ├── kmp-facelink-vmc          → depends on core, networking
  ├── kmp-facelink-ifm          → depends on core, networking
  ├── kmp-facelink-stream       → depends on core (JVM only)
  ├── kmp-facelink-vts          → depends on core (JVM only)
  ├── kmp-facelink-interact     → depends on core
  ├── kmp-facelink-compose      → depends on core
  └── kmp-facelink-capture      → depends on core (internal, not published)
```

### Source Set Layout

Each library module follows this pattern:

```
<module>/src/
  ├── commonMain/      # Shared API and logic
  ├── commonTest/      # Unit tests
  ├── androidMain/     # Android platform impl (if needed)
  ├── iosMain/         # iOS platform impl (if needed)
  ├── nonMobileMain/   # Shared JVM + Wasm no-op stubs (if needed)
  └── wasmJsMain/      # Wasm/JS platform impl (if needed)
```

Modules with platform code: `kmp-facelink` (android/ios/jvm/wasmJs), `kmp-facelink-networking` (android/ios), `kmp-facelink-voice` (android/ios), `kmp-facelink-llm` (android/ios), `kmp-facelink-compose` (android/ios/jvm), `kmp-facelink-capture` (android/ios)
Pure common modules: `kmp-facelink-pipeline`, `kmp-facelink-vmc`, `kmp-facelink-ifm`, `kmp-facelink-interact`
Wasm-enabled common modules: `kmp-facelink-avatar` (commonMain + wasmJsMain)
JVM-only modules: `kmp-facelink-stream`, `kmp-facelink-vts` (JVM target only, no Android/iOS)
Wasm modules: `kmp-facelink` and `kmp-facelink-avatar` include a `wasmJs` target (face/hand/body tracking is implemented via MediaPipe JS interop; holistic is a no-op stub; smoothing and depth estimation are not available on Wasm; avatar mapping is available)
Note: `kmp-facelink` has `jvmMain` (ONNX Runtime + JavaCV) and `nonMobileMain` (shared no-op stubs for JVM + Wasm) source sets.
Note: `kmp-facelink-llm` includes platform-specific inference engines (llama.cpp on Android, Apple Foundation Models on iOS 26+) for on-device LLM.

## Commands

```bash
# Build
./gradlew build                              # Build all modules
./gradlew :<module>:build                    # Build specific module

# Test
./scripts/setup-openseeface-models.sh        # Pinned JVM models; required before JVM tests/builds
./gradlew allTests                           # All tests across all modules
./gradlew :<module>:allTests                 # Tests for a specific module

# Sample apps
./gradlew :androidApp:installDebug           # Install Android sample

# Code quality
./gradlew detekt                             # Static analysis (all modules)
./gradlew sdkQualityCheck                    # Local source/API/platform/capture/privacy gate
```

## Architecture

### Tech Stack

- **Kotlin 2.3.10** — KMP with `explicitApi()` on all modules
- **Android**: MediaPipe 0.10.35, CameraX 1.5.1, Compose
- **iOS**: ARKit, Vision framework, SwiftUI
- **Networking**: Ktor 3.4.0 (OkHttp on Android, Darwin on iOS)
- **Serialization**: kotlinx-serialization-json 1.9.0
- **Coroutines**: kotlinx-coroutines 1.10.2
- **Interop**: SKIE 0.10.10 (Swift-Kotlin Flow bridging)
- **Build**: AGP 9.1.0, Detekt 1.23.8, Dokka 2.1.0
- **On-device AI**: llama.cpp (Android JNI), LiteRT-LM scaffold (Android GPU/NPU), Apple Foundation Models (iOS 26+)

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

**JVM/Desktop (experimental OpenSeeFace estimator)**
- Source build prerequisite: `./scripts/setup-openseeface-models.sh`
- Runtime verifies the pinned FP32 model SHA-256 and exact ONNX tensor contract before camera start
- CI executes the real models with synthetic zero input; this is a mechanics check, not accuracy or FPS evidence
- Do not enable the unqualified INT8 research output or claim desktop product readiness without Tier 3 evidence

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
| LLM | `ModelManager`, `ModelRegistry` | Model download/cache, on-device inference (llama.cpp), Gemma 4 E2B/E4B support |
| LLM | `ModelFormat`, `OnDeviceBackend` | Dual-backend config: GGUF (llama.cpp) or LiteRT-LM format; AUTO/LLAMA_CPP/LITERT_LM backend selection |
| LLM | `FoundationModelsProvider`, `FoundationModelsProviderRegistry` | Apple Foundation Models bridge (iOS 26+, no download needed) |
| Pipeline | `AvatarPipeline`, `buildAvatarPipeline {}` | `listen()`, `processText()`, `output: Flow<AvatarPipelineOutput>`, streaming LLM→TTS→LipSync |
| VMC | `VmcSender`, `VmcReceiver` | `start()`/`stop()`, UDP OSC packets, VRM 1.0 expressions, exponential backoff |
| IFM | `IfmSender`, `IfmReceiver` | `start()`/`stop()`, UDP packets, handshake, connection drop recovery |
| VTS | `VtsClient`, `VtsSender` | WebSocket Plugin API, token persistence, auto-reconnection |

### Key Design Decisions

- **BlendShape naming**: ARKit conventions (52 parameters). MediaPipe landmarks are mapped to ARKit-compatible names
- **Data streaming**: Always `Flow<T>`, never callbacks
- **Thread safety**: `PlatformLock` (ReentrantLock on Android, NSLock on iOS) + `AtomicInt`
- **Head transform**: Right-handed coordinate system
- **Quality boundary**: source and synthetic checks are not device performance or expression-quality evidence; use the separate private replay and device qualification gates

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
- **Guided capture** (`captureApp/`, `captureIosApp/`): internal Android/iOS evaluation apps backed by `kmp-facelink-capture`; not published SDK artifacts

## Language

All generated content must be in English: code, comments, commit messages, PR titles/descriptions, documentation, and TASKS.md entries. Communicate with users in their preferred language.
