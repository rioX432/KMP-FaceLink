# Changelog

## [0.1.0] - 2026-03-24

### Added
- Initial Unity plugin package structure (UPM-compatible)
- C# data models matching KMP-FaceLink core types (BlendShape, HeadTransform, FaceTrackingData, HandTrackingData)
- Platform-specific native bridges:
  - Android: AndroidJavaObject-based JNI bridge to KMP-FaceLink AAR
  - iOS: DllImport-based P/Invoke bridge to KMP-FaceLink XCFramework
  - Editor: Simulated tracking data for development without a device
- FaceLinkManager MonoBehaviour as the main entry point
- Right-handed to left-handed coordinate conversion helpers
- Sample: Face tracking demo with UI visualization
- Sample: Live2D Cubism integration with ARKit-to-Live2D parameter mapping
- Integration guide with setup instructions for Android, iOS, Live2D, and VRM
