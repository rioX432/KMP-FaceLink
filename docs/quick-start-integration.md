# Quick-Start Integration Guide

Add KMP-FaceLink to your project via GitHub Packages (Android/JVM/Wasm) or Swift Package Manager (iOS).

> **Versioning:** Every merge to `master` publishes a unique, immutable version of the form `0.<minor>.<UTC YYYYMMDDHHMM>` (e.g. `0.3.202606290230`) to GitHub Packages and creates a matching GitHub Release with the iOS XCFrameworks. Pick the latest version from [Packages](https://github.com/rioX432?tab=packages&repo_name=KMP-FaceLink) (Maven) or [Releases](https://github.com/rioX432/KMP-FaceLink/releases) (SPM). See [Publishing](publishing.md) for details.

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
kmp-facelink = "0.3.0"

[libraries]
kmp-facelink = { module = "com.anotherball:kmp-facelink", version.ref = "kmp-facelink" }
kmp-facelink-avatar = { module = "com.anotherball:kmp-facelink-avatar", version.ref = "kmp-facelink" }
kmp-facelink-actions = { module = "com.anotherball:kmp-facelink-actions", version.ref = "kmp-facelink" }
kmp-facelink-voice = { module = "com.anotherball:kmp-facelink-voice", version.ref = "kmp-facelink" }
kmp-facelink-llm = { module = "com.anotherball:kmp-facelink-llm", version.ref = "kmp-facelink" }
kmp-facelink-pipeline = { module = "com.anotherball:kmp-facelink-pipeline", version.ref = "kmp-facelink" }
kmp-facelink-vmc = { module = "com.anotherball:kmp-facelink-vmc", version.ref = "kmp-facelink" }
kmp-facelink-ifm = { module = "com.anotherball:kmp-facelink-ifm", version.ref = "kmp-facelink" }
kmp-facelink-stream = { module = "com.anotherball:kmp-facelink-stream", version.ref = "kmp-facelink" }
kmp-facelink-vts = { module = "com.anotherball:kmp-facelink-vts", version.ref = "kmp-facelink" }
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
    implementation(platform("com.anotherball:kmp-facelink-bom:0.3.0"))
    implementation("com.anotherball:kmp-facelink")          // No version needed
    implementation("com.anotherball:kmp-facelink-avatar")   // No version needed
    implementation("com.anotherball:kmp-facelink-actions")  // No version needed
    // Add other modules as needed
}
```

### Direct Dependency (without BOM or version catalog)

```kotlin
dependencies {
    implementation("com.anotherball:kmp-facelink:0.3.0")
}
```

### Repository

KMP-FaceLink is published to **GitHub Packages**. Add the Maven repository and authenticate with a GitHub token that has the `read:packages` scope (GitHub Packages requires authentication even for public packages):

```kotlin
repositories {
    mavenCentral() // for transitive dependencies
    maven {
        url = uri("https://maven.pkg.github.com/rioX432/KMP-FaceLink")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Provide `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties` (or `GITHUB_ACTOR` / `GITHUB_TOKEN` env vars in CI).

## iOS (Swift Package Manager)

### Xcode

1. Open your project in Xcode
2. Go to **File > Add Package Dependencies...**
3. Enter the repository URL: `https://github.com/rioX432/KMP-FaceLink`
4. Select the version rule (e.g., **Up to Next Major Version** from `0.3.0`)
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
    .package(url: "https://github.com/rioX432/KMP-FaceLink", from: "0.3.0"),
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

Full API documentation is available at: https://riox432.github.io/KMP-FaceLink/

## Next Steps

- [Android Quick Start](quick-start-android.md) — Camera setup, permissions, and sample app
- [iOS Quick Start](quick-start-ios.md) — ARKit configuration and SwiftUI integration
- [API Overview](api-overview.md) — Complete API surface documentation
