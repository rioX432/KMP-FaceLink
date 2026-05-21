# Quick-Start Integration Guide

Add KMP-FaceLink to your project via Maven Central (Android/JVM) or Swift Package Manager (iOS).

> **Note:** KMP-FaceLink is not yet published to Maven Central or SPM. The publishing infrastructure (Gradle signing, Sonatype nexus-publish, GitHub Actions release workflow, SPM binary targets) is fully configured and will activate on the first `v*` tag push. Until then, build locally with `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories. See [Publishing](publishing.md) for the full release checklist.

## Requirements

| Platform | Requirement |
|----------|-------------|
| Android | compileSdk 35+, minSdk 24 |
| iOS | iOS 15+ |

## Android / Kotlin Multiplatform (Gradle)

### Version Catalog (recommended)

Define the version once in `gradle/libs.versions.toml` and reference it across all modules:

```toml
[versions]
kmp-facelink = "0.1.0"

[libraries]
kmp-facelink = { module = "io.github.rioX432:kmp-facelink", version.ref = "kmp-facelink" }
kmp-facelink-avatar = { module = "io.github.rioX432:kmp-facelink-avatar", version.ref = "kmp-facelink" }
kmp-facelink-actions = { module = "io.github.rioX432:kmp-facelink-actions", version.ref = "kmp-facelink" }
kmp-facelink-voice = { module = "io.github.rioX432:kmp-facelink-voice", version.ref = "kmp-facelink" }
kmp-facelink-llm = { module = "io.github.rioX432:kmp-facelink-llm", version.ref = "kmp-facelink" }
kmp-facelink-pipeline = { module = "io.github.rioX432:kmp-facelink-pipeline", version.ref = "kmp-facelink" }
kmp-facelink-vmc = { module = "io.github.rioX432:kmp-facelink-vmc", version.ref = "kmp-facelink" }
kmp-facelink-ifm = { module = "io.github.rioX432:kmp-facelink-ifm", version.ref = "kmp-facelink" }
kmp-facelink-stream = { module = "io.github.rioX432:kmp-facelink-stream", version.ref = "kmp-facelink" }
kmp-facelink-vts = { module = "io.github.rioX432:kmp-facelink-vts", version.ref = "kmp-facelink" }
```

Then in your KMP module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kmp.facelink)          // Core tracking
            implementation(libs.kmp.facelink.avatar)   // Live2D/VRM mapping
            implementation(libs.kmp.facelink.actions)  // Gesture triggers
            // Add other modules as needed
        }
    }
}
```

### BOM (Android-only projects)

> **KMP limitation:** Gradle's `platform()` function cannot be used in `commonMain.dependencies {}` ([KT-58759](https://youtrack.jetbrains.com/issue/KT-58759)). The BOM approach only works in Android-only (non-KMP) projects. For KMP projects, use the version catalog approach above.

If your project is Android-only (not KMP), the BOM aligns all module versions automatically:

```kotlin
dependencies {
    implementation(platform("io.github.rioX432:kmp-facelink-bom:0.1.0"))
    implementation("io.github.rioX432:kmp-facelink")          // No version needed
    implementation("io.github.rioX432:kmp-facelink-avatar")   // No version needed
    implementation("io.github.rioX432:kmp-facelink-actions")  // No version needed
    // Add other modules as needed
}
```

### Direct Dependency (without BOM or version catalog)

```kotlin
dependencies {
    implementation("io.github.rioX432:kmp-facelink:0.1.0")
}
```

### Repository

KMP-FaceLink is published to Maven Central. No additional repository configuration is needed if you already have `mavenCentral()` in your `repositories` block:

```kotlin
repositories {
    mavenCentral()
}
```

## iOS (Swift Package Manager)

### Xcode

1. Open your project in Xcode
2. Go to **File > Add Package Dependencies...**
3. Enter the repository URL: `https://github.com/rioX432/KMP-FaceLink`
4. Select the version rule (e.g., **Up to Next Major Version** from `0.1.0`)
5. Choose the libraries you need:
   - `KMPFaceLink` — Core tracking
   - `KMPFaceLinkAvatar` — Live2D/VRM parameter mapping
   - `KMPFaceLinkActions` — Gesture and expression triggers
   - `KMPFaceLinkVoice` — ASR, TTS, and lip sync
   - `KMPFaceLinkLlm` — LLM streaming API
   - `KMPFaceLinkPipeline` — Full avatar pipeline
   - `KMPFaceLinkNetworking` — UDP networking utilities
   - `KMPFaceLinkVMC` — VMC Protocol support
   - `KMPFaceLinkIFM` — iFacialMocap protocol

### Package.swift

```swift
dependencies: [
    .package(url: "https://github.com/rioX432/KMP-FaceLink", from: "0.1.0"),
],
targets: [
    .target(
        name: "YourApp",
        dependencies: [
            .product(name: "KMPFaceLink", package: "KMP-FaceLink"),
            .product(name: "KMPFaceLinkAvatar", package: "KMP-FaceLink"),
            // Add other modules as needed
        ]
    ),
]
```

## Module Overview

| Module | Maven Artifact | SPM Product | Description |
|--------|---------------|-------------|-------------|
| BOM | `kmp-facelink-bom` | -- | Version alignment (Android-only; not usable in KMP `commonMain`) |
| Core | `kmp-facelink` | `KMPFaceLink` | Face, hand, and body tracking |
| Avatar | `kmp-facelink-avatar` | `KMPFaceLinkAvatar` | Live2D and VRM parameter mapping |
| Actions | `kmp-facelink-actions` | `KMPFaceLinkActions` | Gesture and expression triggers |
| Voice | `kmp-facelink-voice` | `KMPFaceLinkVoice` | ASR, TTS, and lip sync |
| LLM | `kmp-facelink-llm` | `KMPFaceLinkLlm` | LLM streaming (cloud + on-device) |
| Pipeline | `kmp-facelink-pipeline` | `KMPFaceLinkPipeline` | Voice → LLM → TTS → Animation |
| Networking | `kmp-facelink-networking` | `KMPFaceLinkNetworking` | Shared UDP socket utilities |
| VMC | `kmp-facelink-vmc` | `KMPFaceLinkVMC` | VMC Protocol (OSC over UDP) |
| IFM | `kmp-facelink-ifm` | `KMPFaceLinkIFM` | iFacialMocap protocol output |
| Stream | `kmp-facelink-stream` | — | WebSocket streaming (JVM only) |
| VTS | `kmp-facelink-vts` | — | VTube Studio API (JVM only) |

## Minimal Example

### Android (Kotlin)

```kotlin
val tracker = FaceTrackerFactory.create(
    config = FaceTrackerConfig(),
    context = applicationContext,
)

// Collect tracking data
lifecycleScope.launch {
    tracker.trackingData.collect { data ->
        // data.blendShapes contains 52 ARKit blend shapes
    }
}

tracker.start()
```

### iOS (Swift)

```swift
import KMPFaceLink

let tracker = FaceTrackerFactory.companion.create(config: FaceTrackerConfig())

// Observe tracking data via SKIE-bridged Flow
Task {
    for await data in tracker.trackingData {
        // data.blendShapes contains 52 ARKit blend shapes
    }
}

tracker.start()
```

## API Reference

Full API documentation is available at: https://rioX432.github.io/KMP-FaceLink/

## Next Steps

- [Android Quick Start](quick-start-android.md) — Camera setup, permissions, and sample app
- [iOS Quick Start](quick-start-ios.md) — ARKit configuration and SwiftUI integration
- [API Overview](api-overview.md) — Complete API surface documentation
