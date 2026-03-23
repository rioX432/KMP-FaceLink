# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- AI avatar pipeline module (`kmp-facelink-pipeline`) with streaming LLM → TTS → LipSync → Expression chain (583315b)
- `AvatarPipeline` interface with builder DSL (`buildAvatarPipeline {}`) (b15b0e4)
- `DefaultAvatarPipeline` orchestrator with state management and error recovery (57c50d0)
- `StreamingCoordinator` for sentence-buffered streaming pipeline (ebf54a2)
- `ExpressionMapper` pipeline: text sentiment → emotion → BlendShapes (e7dcdfc)
- Integration tests and `PipelineLatencyTracker` for benchmarking
- On-device LLM inference: `LlmConfig.OnDevice`, `InferenceEngine`, `ModelManager` (6e10d4b)
- Android llama.cpp JNI inference engine (c0d0845)
- iOS MLX inference engine stub with Objective-C interop header (fc1797f)
- Hybrid LLM routing with `HybridLlmClient` and `IntentClassifier` (1757ba6)
- Model download & cache management with LRU eviction and SHA-256 verification (da8d992)
- LLM benchmarking framework (`LlmBenchmarkRunner`, `BenchmarkPrompts`) (2fd2cdb)
- JVM target with no-op tracker stubs for desktop support (7eec370)
- Compose Multiplatform `sampleShared` module with shared UI components (763c230)
- Landmark visibility/presence fields and real face confidence instrumentation (f3b37fc)
- VRM parameter mapping for 3D avatar support with VRM 0.x/1.0 conventions (145ecf0)
- Multilingual lip sync with language-specific viseme mappings (8cf270f)
- Object pooling to eliminate per-frame allocations (8eef3bb)
- Thermal throttling mitigation with `ThermalMonitor` and `FrameThrottler` (99057fc)
- `PerformanceMonitor` for tracking FPS and latency (c348ee1)
- Multi-face tracking support (0e01f5e)
- `switchCamera` API for runtime camera switching (f1a0e02)
- Neutral expression auto-detection (6dd8518)
- Category-aware One Euro smoothing (23e1712)
- FACS-based expression correlation (55771c4)
- Geometric cheek/brow/mouth expression solvers (8581493, 7f48381)
- Calibration with warmup and exponential decay (e60209e)

### Changed
- Upgrade to Kotlin 2.2.0 and SKIE 0.10.10 (8266d7d)
- Replace `VtsConfig.onTokenReceived` callback with SharedFlow (1691a82)
- Refactor `VtsStreamClient.handleMessage` into handler map (bb9b74e)

### Fixed
- Thread-safe `FrameThrottler`, observer queue, thermal state consistency (43f0ec2)
- Eye blink suppression with sensitivity boost and OneEuro smoothing (e9f5153)
- iOS `processBuffer` data race with NSRecursiveLock (215dab2)
- `HttpClient` leak: `VoicePipeline` implements `AutoCloseable` (222c472)
- `WhisperCppBridge` contextPtr race with synchronized lock (5ebde7f)
- `VoicePipeline` error handling: propagate play failure (8a6ba02)
- `inFlightBitmap` race: use `AtomicReference` for atomic bitmap lifecycle (448e223)
- `explicitApi` violations: add public to `GazeData` and `HolisticTrackingData` (e7a22ea)
- iOS `currentTimeMillis()` float precision loss via `clock_gettime` (f9caf4c)

### Infrastructure
- Dependabot for automated dependency updates (ea4f539)
- Bump actions/checkout 4→6, actions/cache 4→5, actions/setup-java 4→5, gradle/actions 4→5

## [v0.1.0] — 2026-03-01

Initial release with core tracking, avatar mapping, actions, voice, and LLM modules.
