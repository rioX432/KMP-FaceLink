# KMP-FaceLink Unity Plugin — Integration Guide

## Overview

The KMP-FaceLink Unity plugin provides real-time face and hand tracking for VTuber avatar rendering. It wraps the native KMP-FaceLink libraries (MediaPipe on Android, ARKit/Vision on iOS) with a unified C# API designed for Unity's frame-based update loop.

## Requirements

- Unity 2021.3 LTS or later
- Android: API 24+ (MediaPipe requirement)
- iOS: 17.0+ (ARKit face tracking requires TrueDepth camera)

## Installation

### Option 1: Git URL (recommended)

In Unity, go to **Window > Package Manager > + > Add package from git URL**:

```
https://github.com/AnotherBall/KMP-FaceLink.git?path=unity-plugin
```

### Option 2: Local path

Clone the repo and add via local path:

```
/path/to/KMP-FaceLink/unity-plugin
```

## Quick Start

### 1. Add FaceLinkManager to your scene

1. Create an empty GameObject named `FaceLinkManager`
2. Add the `KmpFaceLink.FaceLinkManager` component
3. Configure tracking options in the Inspector:
   - **Enable Face Tracking**: On (default)
   - **Enable Hand Tracking**: Off (default)
   - **Auto Start**: On (default)

### 2. Subscribe to tracking events

```csharp
using KmpFaceLink;
using UnityEngine;

public class MyAvatarController : MonoBehaviour
{
    [SerializeField] private FaceLinkManager faceLinkManager;

    private void OnEnable()
    {
        faceLinkManager.OnFaceDataUpdated += OnFaceData;
        faceLinkManager.OnHandDataUpdated += OnHandData;
    }

    private void OnDisable()
    {
        faceLinkManager.OnFaceDataUpdated -= OnFaceData;
        faceLinkManager.OnHandDataUpdated -= OnHandData;
    }

    private void OnFaceData(FaceTrackingData data)
    {
        // Read blend shapes (52 ARKit-compatible values, 0.0–1.0)
        float eyeBlinkLeft = data.GetBlendShape(BlendShape.EyeBlinkLeft);
        float jawOpen = data.GetBlendShape(BlendShape.JawOpen);

        // Read head transform (right-hand → left-hand conversion built in)
        Quaternion headRotation = data.HeadTransform.ToUnityRotation();
        Vector3 headPosition = data.HeadTransform.ToUnityPosition();
    }

    private void OnHandData(HandTrackingData data)
    {
        for (int i = 0; i < data.HandCount; i++)
        {
            TrackedHand hand = data.Hands[i];
            Vector3 wristPos = hand.GetLandmark(HandJoint.Wrist).ToUnityPosition();
            // ... use 21 landmarks per hand
        }
    }
}
```

### 3. Or poll data directly

```csharp
private void Update()
{
    if (faceLinkManager.IsTracking)
    {
        FaceTrackingData face = faceLinkManager.LatestFaceData;
        float smile = face.GetBlendShape(BlendShape.MouthSmileLeft);
    }
}
```

## Native Library Setup

The C# wrapper requires the native KMP-FaceLink libraries to be present in the build.

### Android (.aar)

1. Build the AAR from the KMP-FaceLink project:
   ```bash
   ./gradlew :kmp-facelink:assembleRelease
   ```
2. Copy `kmp-facelink/build/outputs/aar/kmp-facelink-release.aar` to:
   ```
   unity-plugin/Runtime/Plugins/Android/kmp-facelink-release.aar
   ```
3. In Unity's Plugin Inspector, ensure the AAR targets Android only.

The Android bridge uses `AndroidJavaObject` to call into the AAR via JNI. A thin Java facade class (`FaceLinkUnityFacade`) converts KMP-FaceLink's Kotlin Flow-based API into a pollable buffer.

### iOS (.xcframework)

1. Build the XCFramework from the KMP-FaceLink project:
   ```bash
   ./gradlew :kmp-facelink:assembleXCFramework
   ```
2. Copy the XCFramework to your Unity project or use a post-process build script to embed it.
3. The iOS bridge uses `[DllImport("__Internal")]` (static linking) with a thin C wrapper (`KmpFaceLinkBridge.m`).

**Required iOS capabilities** (add to your Xcode project):
- Camera Usage Description
- ARKit (for face tracking)

## Live2D Integration

See the `Live2DIntegration` sample for a complete example. Key parameter mapping:

| KMP-FaceLink (ARKit) | Live2D Parameter | Notes |
|---|---|---|
| `EyeBlinkLeft` | `ParamEyeLOpen` | Invert: `1 - blinkValue` |
| `EyeBlinkRight` | `ParamEyeROpen` | Invert: `1 - blinkValue` |
| `JawOpen` | `ParamMouthOpenY` | Direct map |
| `MouthSmileLeft/Right` | `ParamMouthForm` | Average L+R |
| `HeadTransform.Yaw` | `ParamAngleX` | Scale to -30..30 range |
| `HeadTransform.Pitch` | `ParamAngleY` | Scale to -30..30 range |
| `HeadTransform.Roll` | `ParamAngleZ` | Scale to -30..30 range |

## VRM Integration

For VRM (UniVRM/VRM-0.x/VRM-1.0), the blend shape names map directly since both VRM and KMP-FaceLink use the ARKit 52 blend shape convention:

```csharp
// VRM blend shape proxy example
var proxy = GetComponent<VRMBlendShapeProxy>();
foreach (BlendShape shape in System.Enum.GetValues(typeof(BlendShape)))
{
    float value = faceData.GetBlendShape(shape);
    // VRM uses the same ARKit names
    proxy.ImmediatelySetValue(BlendShapeKey.CreateFromPreset(shape.ToString()), value);
}
```

## Coordinate System

KMP-FaceLink uses a **right-handed** coordinate system (same as ARKit/MediaPipe). Unity uses **left-handed**. The plugin provides conversion helpers:

- `HeadTransform.ToUnityRotation()` — flips yaw and roll signs
- `HeadTransform.ToUnityPosition()` — flips X-axis
- `HandLandmarkPoint.ToUnityPosition()` — flips Y-axis (top-down → bottom-up)

## Architecture

```
Unity C# (FaceLinkManager)
    │
    ├── Android: AndroidJavaObject ──→ JNI ──→ Kotlin AAR (MediaPipe)
    │
    ├── iOS: DllImport("__Internal") ──→ C bridge ──→ Swift/K/N (ARKit)
    │
    └── Editor: Simulated data (sine waves for testing)
```

The plugin uses a **poll-based** architecture instead of callbacks:
- Native trackers run on background threads and buffer the latest frame
- `FaceLinkManager.Update()` polls new data each Unity frame
- Events (`OnFaceDataUpdated`) fire on the main thread — safe for UI/renderer access
- Data objects are reused to minimize GC allocation

## Troubleshooting

### No tracking data on device
- Verify the native library (.aar/.xcframework) is included in the build
- Check Android logcat / Xcode console for `[KmpFaceLink]` log messages
- Ensure camera permissions are granted

### Editor shows no data
- The editor bridge provides simulated data automatically
- If not working, check that `FaceLinkManager.AutoStart` is enabled

### Performance
- Face tracking targets 30-60 FPS depending on device
- Hand tracking adds ~5ms per frame — disable if not needed
- Use the `_smoothing` parameter in Live2D scripts to trade latency for smoothness
