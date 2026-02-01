# KMP-FaceLink

Unified face tracking API for Kotlin Multiplatform (KMP).

Wraps platform-specific face tracking SDKs — **MediaPipe** (Android) and **ARKit** (iOS) — into a single shared Kotlin API, providing a consistent stream of facial blend shapes and head transform data across platforms.

## Motivation

Building a VTuber or AR app with KMP today means writing separate face tracking code for each platform. KMP-FaceLink eliminates this duplication by providing one API that works on both Android and iOS.

## Features (Planned)

- Unified face tracking API in `commonMain`
- **Android**: MediaPipe Face Landmarker integration
- **iOS**: ARKit ARFaceTrackingConfiguration integration
- 52+ ARKit-compatible blend shape parameters
- Head position and rotation (6DoF)
- Calibration support
- Real-time performance (targeting 30-60fps)

## Architecture

```
commonMain/
├── FaceTracker.kt          # Public API interface
├── FaceTrackingData.kt     # Unified data model
├── BlendShape.kt           # Blend shape definitions
└── CalibrationConfig.kt    # Calibration settings

androidMain/
└── MediaPipeFaceTracker.kt # MediaPipe implementation

iosMain/
└── ARKitFaceTracker.kt     # ARKit implementation
```

## Blend Shape Parameters

Provides a unified set of facial parameters including:

- **Eyes**: blink, gaze direction, squint, wide
- **Brows**: inner up, down, outer up
- **Mouth**: open, smile, pucker, funnel, and more
- **Cheeks**: puff, squint
- **Jaw**: open, forward, left/right
- **Head**: rotation (pitch, yaw, roll) + position (x, y, z)

## Documentation

API documentation is available at **[riox432.github.io/KMP-FaceLink](https://riox432.github.io/KMP-FaceLink/)**.

## Tech Stack

- Kotlin Multiplatform (KMP)
- MediaPipe Face Landmarker (Android)
- ARKit (iOS)
- Kotlin Coroutines / Flow

## Status

**Early stage** — architecture design and API definition in progress.

## Contributing

Contributions are welcome! Please open an issue to discuss your ideas before submitting a PR.

## License

[MIT](LICENSE)
