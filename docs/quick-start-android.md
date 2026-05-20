# Quick Start: Android

Get face tracking running on Android in under 5 minutes.

## 1. Add Dependencies

In your module-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.rioX432:kmp-facelink:0.1.0")
    // Optional modules — add only what you need:
    // implementation("io.github.rioX432:kmp-facelink-avatar:0.1.0")
    // implementation("io.github.rioX432:kmp-facelink-actions:0.1.0")
}
```

> **Note:** KMP-FaceLink is not yet published to Maven Central. For now, use the library as a local project dependency or Git submodule.

## 2. Download the MediaPipe Model

The face tracker requires a MediaPipe Face Landmarker model. Download it into `src/main/assets/models/`:

```bash
mkdir -p app/src/main/assets/models
curl -L -o app/src/main/assets/models/face_landmarker_v2_with_blendshapes.task \
  "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
```

For hand tracking, also download:

```bash
curl -L -o app/src/main/assets/models/hand_landmarker.task \
  "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"
```

## 3. Add Permissions

In `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="true" />
```

Request runtime camera permission before starting the tracker (Android 6+).

## 4. Start Face Tracking

```kotlin
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.model.FaceTrackerConfig

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = PlatformContext(
            context = applicationContext,
            lifecycleOwner = this,
        )
        val tracker = createFaceTracker(context, FaceTrackerConfig())

        lifecycleScope.launch {
            tracker.start()
            tracker.trackingData.collect { data ->
                if (data.isTracking) {
                    val jawOpen = data.blendShapes[BlendShape.JAW_OPEN] ?: 0f
                    Log.d("FaceLink", "jawOpen=$jawOpen")
                }
            }
        }
    }
}
```

## 5. Observe Tracking State

```kotlin
lifecycleScope.launch {
    tracker.state.collect { state ->
        when (state) {
            TrackingState.TRACKING -> Log.d("FaceLink", "Face detected")
            TrackingState.ERROR -> Log.e("FaceLink", tracker.errorMessage.value ?: "Unknown error")
            else -> {}
        }
    }
}
```

## 6. Add Hand Tracking (Optional)

```kotlin
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.model.HandTrackerConfig

val handTracker = createHandTracker(context, HandTrackerConfig())

lifecycleScope.launch {
    handTracker.start()
    handTracker.trackingData.collect { data ->
        data.hands.forEach { hand ->
            Log.d("FaceLink", "gesture=${hand.gesture}, hand=${hand.handedness}")
        }
    }
}
```

## 7. Holistic Tracking (Face + Hands + Body)

Use `createHolisticTracker` for simultaneous multi-modality tracking with a shared camera session:

```kotlin
import io.github.kmpfacelink.api.createHolisticTracker
import io.github.kmpfacelink.model.HolisticTrackerConfig

val holisticTracker = createHolisticTracker(
    context,
    HolisticTrackerConfig(enableFace = true, enableHands = true, enableBody = true),
)

lifecycleScope.launch {
    holisticTracker.start()
    holisticTracker.trackingData.collect { data ->
        data.face?.let { /* face blend shapes */ }
        data.hands?.let { /* hand landmarks */ }
        data.body?.let { /* body pose */ }
    }
}
```

## Configuration Options

`FaceTrackerConfig` supports tuning:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `smoothingConfig` | `Ema(alpha=0.5)` | Smoothing filter: `None`, `Ema`, `OneEuro`, `CategoryAware` |
| `cameraFacing` | `FRONT` | `FRONT` or `BACK` camera |
| `maxFaces` | `1` | Simultaneous face count (1-4) |
| `powerMode` | `null` | Power management: `Performance`, `Balanced`, `PowerSaver` |
| `enhancerConfig` | `Default` | Geometric blend shape enhancement (Android only) |

## Requirements

- Min SDK 24
- Camera permission granted at runtime
- MediaPipe model file in `assets/models/`

## Next Steps

- [API Overview](api-overview.md) for all modules and use cases
- [iOS Quick Start](quick-start-ios.md) for the iOS integration
- [Troubleshooting](troubleshooting.md) for common integration issues
