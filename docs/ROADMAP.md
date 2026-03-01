# Roadmap

KMP-FaceLink development roadmap. Phases are sequential but individual items may ship out of order based on community demand.

Want to influence priorities? [Open an issue](https://github.com/rioX432/KMP-FaceLink/issues/new/choose) or upvote existing ones.

---

## Positioning

KMP-FaceLink is a Kotlin Multiplatform library that exposes a unified API for face, hand, and body tracking on Android (MediaPipe) and iOS (ARKit / Vision).

The core value:
1. **Track** — real-time face/hand/body data from device cameras
2. **Map** — blend shapes, gestures, and transforms to avatar rigs (Live2D, VTubeStudio, Rive)
3. **Extend** — plug into voice, LLM, and effects pipelines via optional modules

---

## Phase 1 -- Core Face Tracking (Complete)

Foundation: cross-platform face tracking API.

- [x] KMP project setup (Android + iOS + Shared)
- [x] Common data models (`BlendShape`, `HeadTransform`, `FaceTrackingData`)
- [x] `FaceTracker` API, smoothing / calibration utilities
- [x] Android: MediaPipeFaceTracker
- [x] iOS: ARKitFaceTracker
- [x] One Euro Filter + EMA + runtime filter switching
- [x] BlendShapeEnhancer for MediaPipe low-accuracy shapes
- [x] Thread safety with PlatformLock
- [x] Unit tests (BlendShape, EMA, Calibrator, TransformUtils)
- [x] CI/CD with GitHub Actions and Detekt static analysis

## Phase 2 -- Extended Tracking (Complete)

Hand, body, and holistic tracking.

- [x] Hand tracking with gesture recognition (Android MediaPipe + iOS Vision)
- [x] Body tracking (Android MediaPipe + iOS Vision)
- [x] Holistic tracking: composable Face + Hand + Body with shared camera session
- [x] `@ExperimentalFaceLinkApi`, `explicitApi()`, semver stability docs

## Phase 3 -- Integration Modules (Complete)

Eight optional extension modules for avatar and VTuber workflows.

- [x] `kmp-facelink-avatar` — Live2D parameter mapping
- [x] `kmp-facelink-effects` — renderer-agnostic face effects engine
- [x] `kmp-facelink-actions` — gesture / expression trigger system
- [x] `kmp-facelink-live2d` — Live2D SDK integration + PerfectSync mapping
- [x] `kmp-facelink-stream` — VTubeStudio WebSocket API client
- [x] `kmp-facelink-voice` — ASR, TTS, and lip sync
- [x] `kmp-facelink-rive` — Rive avatar integration
- [x] `kmp-facelink-llm` — LLM streaming API (OpenAI, Anthropic, Gemini)
- [x] iOS whisper.cpp cinterop for on-device ASR

---

## Phase 4 -- Distribution & Documentation (Next)

Make the library usable by the broader community.

- [ ] [#118](https://github.com/rioX432/KMP-FaceLink/issues/118) Host API documentation on GitHub Pages (Dokka)
- [ ] [#119](https://github.com/rioX432/KMP-FaceLink/issues/119) Getting started guide and integration cookbook
- [ ] [#126](https://github.com/rioX432/KMP-FaceLink/issues/126) SPM / CocoaPods distribution for iOS
- [x] Maven Central publishing setup

**DoD**: library installable via Maven Central and SPM; Dokka docs live at GitHub Pages; quickstart guide covers face tracking in under 10 minutes

---

## Phase 5 -- Quality & Tech Debt

Raise test coverage, fix platform parity gaps, and resolve tech debt.

### Testing

- [ ] [#111](https://github.com/rioX432/KMP-FaceLink/issues/111) Core module test coverage (32% → 60%)
- [ ] [#112](https://github.com/rioX432/KMP-FaceLink/issues/112) Voice module test coverage (31% → 50%)
- [ ] [#113](https://github.com/rioX432/KMP-FaceLink/issues/113) LLM module: SSE parser and provider tests
- [ ] [#114](https://github.com/rioX432/KMP-FaceLink/issues/114) Stream module: reconnection and edge case tests
- [ ] [#124](https://github.com/rioX432/KMP-FaceLink/issues/124) Performance benchmarking suite

### Platform Parity

- [ ] [#139](https://github.com/rioX432/KMP-FaceLink/issues/139) iOS AudioRecorder ignores format parameter
- [ ] [#140](https://github.com/rioX432/KMP-FaceLink/issues/140) iOS PlatformAudioRecorder lacks CoroutineScope / Job management
- [ ] [#145](https://github.com/rioX432/KMP-FaceLink/issues/145) iOS VisionHandTracker: document revision pinning
- [ ] [#146](https://github.com/rioX432/KMP-FaceLink/issues/146) currentTimeMillis() floating-point precision loss on iOS

### API & Architecture

- [ ] [#117](https://github.com/rioX432/KMP-FaceLink/issues/117) Refactor VtsStreamClient.handleMessage complexity
- [ ] [#137](https://github.com/rioX432/KMP-FaceLink/issues/137) VtsConfig.onTokenReceived → `Flow<String>`
- [ ] [#147](https://github.com/rioX432/KMP-FaceLink/issues/147) kmp-facelink-live2d modular design (remove cross-module dependencies)

### Infrastructure

- [ ] [#127](https://github.com/rioX432/KMP-FaceLink/issues/127) Dependabot / Renovate for automated dependency updates
- [ ] [#128](https://github.com/rioX432/KMP-FaceLink/issues/128) Kotlin 2.2 migration preparation

---

## Phase 6 -- iOS Renderer Bridges

Bring Live2D and Rive rendering fully to iOS via Kotlin bridges.

- [ ] [#109](https://github.com/rioX432/KMP-FaceLink/issues/109) iOS Rive renderer Kotlin bridge
- [ ] [#110](https://github.com/rioX432/KMP-FaceLink/issues/110) iOS Live2D renderer Kotlin bridge

**DoD**: Live2D and Rive avatars driven by KMP tracking data on iOS with no Swift glue code required

---

## Phase 7 -- Advanced Features (Future)

Expand platform reach and tracking capabilities.

- [ ] [#120](https://github.com/rioX432/KMP-FaceLink/issues/120) Compose Multiplatform sample app migration
- [ ] [#121](https://github.com/rioX432/KMP-FaceLink/issues/121) Desktop / JVM target for webcam VTubing
- [ ] [#122](https://github.com/rioX432/KMP-FaceLink/issues/122) Multi-face tracking support
- [ ] [#123](https://github.com/rioX432/KMP-FaceLink/issues/123) Camera switching API (front / back)
- [ ] [#125](https://github.com/rioX432/KMP-FaceLink/issues/125) On-device LLM inference (MLX / ONNX Runtime)

---

## Dependency Graph

```
Phase 4: Distribution
          │
Phase 5:  Quality & Tech Debt
          │
Phase 6:  iOS Renderer Bridges (depends on Phase 4 SPM)
          │
Phase 7:  Advanced Features
```

---

## Stability Guarantee

KMP-FaceLink follows [Semantic Versioning](https://semver.org/).

- Public APIs annotated with `@ExperimentalFaceLinkApi` may change without notice.
- Stable APIs (`public` without the annotation) follow semver from v1.0.0.
- Pre-1.0 releases (0.x.y) may have breaking changes between minor versions.
