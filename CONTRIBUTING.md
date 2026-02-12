# Contributing to KMP-FaceLink

Thank you for your interest in contributing! This guide will help you get started.

## Prerequisites

- **JDK 17+**
- **Android SDK** (API 24+, build tools via Android Studio)
- **Xcode 15+** with command-line tools (for iOS builds)
- **Kotlin Multiplatform** toolchain (bundled via Gradle)

## Build & Test

```bash
# Run detekt (static analysis)
./gradlew detekt

# Run all tests
./gradlew :kmp-facelink:allTests

# Full build (Android + iOS frameworks)
./gradlew :kmp-facelink:build

# Android sample app
./gradlew :sample:installDebug
```

## Code Style

- **Detekt** is enforced in CI. Run `./gradlew detekt` before pushing.
- Follow existing patterns: `PlatformLock` for thread safety, `AtomicInt` for flags.
- Keep public API surface minimal — use `internal` by default.

## Pull Request Process

1. **Open an issue first** to discuss the change.
2. Fork the repo and create a feature branch from `master`.
3. Keep commits focused and messages concise (single line).
4. Ensure `./gradlew detekt` and `./gradlew :kmp-facelink:allTests` pass.
5. Submit a PR linking the issue.

## Architecture

The library follows a `commonMain` / `androidMain` / `iosMain` structure:

- **`commonMain`** — Public API interfaces (`FaceTracker`, `HandTracker`) and data models
- **`androidMain`** — MediaPipe + CameraX implementations
- **`iosMain`** — ARKit (face) and Vision framework (hand) implementations

API documentation is published via Dokka at [riox432.github.io/KMP-FaceLink](https://riox432.github.io/KMP-FaceLink/).
