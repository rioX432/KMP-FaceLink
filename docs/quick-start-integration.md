# Quick-Start Integration Guide

Add KMP-FaceLink to your project via Maven Central (Android/JVM) or Swift Package Manager (iOS).

## Android / Kotlin Multiplatform (Gradle)

### Version Catalog (recommended)

Add to `gradle/libs.versions.toml`:

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

Then in your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.kmp.facelink)          // Core tracking
    implementation(libs.kmp.facelink.avatar)   // Live2D/VRM mapping
    implementation(libs.kmp.facelink.actions)  // Gesture triggers
    // Add other modules as needed
}
```

### Direct Dependency

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
