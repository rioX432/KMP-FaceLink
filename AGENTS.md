# AGENTS.md

This file provides guidance to AI coding agents working with this repository.

## Project Overview

KMP-FaceLink is a Kotlin Multiplatform library that provides a unified face tracking API. It wraps MediaPipe (Android) and ARKit (iOS) into a single shared Kotlin interface, enabling cross-platform VTuber and AR applications.

## Commands

```bash
# Build
./gradlew build                      # Build all modules
./gradlew :kmp-facelink:build       # Build library only

# Test
./gradlew :kmp-facelink:allTests    # Run all tests
./gradlew :kmp-facelink:allTests            # All platform tests

# Sample app
./gradlew :androidApp:installDebug      # Install Android sample app

# Code Quality
./gradlew ktlintCheck               # Lint check
./gradlew ktlintFormat              # Auto-format
./gradlew detekt                    # Static analysis
```

## Architecture

### Tech Stack
- Kotlin Multiplatform (KMP) — shared face tracking API
- MediaPipe Face Landmarker — Android face tracking
- ARKit ARFaceTrackingConfiguration — iOS face tracking
- Kotlin Coroutines / Flow — async data streaming
- Koin — dependency injection (optional)

### Module Structure

```
KMP-FaceLink/
├── kmp-facelink/              # KMP library module
│   └── src/
│       ├── commonMain/        # Shared API
│       │   └── kotlin/
│       │       ├── api/              # Public API (FaceTracker, FaceTrackingData)
│       │       ├── model/            # BlendShape, HeadTransform, CalibrationConfig
│       │       └── util/             # Smoothing, threshold utilities
│       ├── androidMain/       # MediaPipe implementation
│       │   └── kotlin/
│       │       └── MediaPipeFaceTracker.kt
│       └── iosMain/           # ARKit implementation
│           └── kotlin/
│               └── ARKitFaceTracker.kt
├── androidApp/                # Sample Android app
│   └── src/main/kotlin/
└── iosApp/                    # Sample iOS app
```

### Key Design Decisions

**Data Model**
- Blend shapes follow ARKit naming conventions (52+ parameters)
- MediaPipe landmarks are mapped to ARKit-compatible blend shapes in the Android implementation
- Head transform uses a right-handed coordinate system

**API Surface**
- `FaceTracker` interface: `start()`, `stop()`, `trackingData: Flow<FaceTrackingData>`
- Platform-specific factory functions: `FaceTracker.create(config)`
- Calibration via `CalibrationConfig` (offset, sensitivity, smoothing)

**Performance**
- Target: 30-60fps real-time tracking
- Smoothing filters to reduce jitter
- Configurable thresholds to ignore micro-movements

## Development Guidelines

- Public API goes in `commonMain` — keep it minimal and clean
- Platform implementations are internal, only exposed through the shared interface
- All parameter names follow ARKit blend shape naming conventions
- Use `Flow<FaceTrackingData>` for streaming data (not callbacks)
- Write unit tests for mapping logic (MediaPipe landmarks → blend shapes)
- Sample apps should demonstrate basic face tracking with a simple avatar

## Language

Communicate in English for this project.
