# Quick Start: Android

Get face tracking running on Android in under 5 minutes.

## 1. Add Dependencies

In your module-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.anotherball:kmp-facelink:0.2.0")
    // Optional modules — add only what you need:
    // implementation("com.anotherball:kmp-facelink-avatar:0.2.0")
    // implementation("com.anotherball:kmp-facelink-actions:0.2.0")
}
```

## 2. Model Setup

MediaPipe requires model files in your `assets/models/` directory. Use one of the manual approaches below to download them.

### Model Overview

| Model | File | Size (approx) |
|-------|------|---------------|
| Face Landmarker | `face_landmarker_v2_with_blendshapes.task` | ~5 MB |
| Hand Landmarker | `hand_landmarker.task` | ~12 MB |
| Pose Landmarker Lite | `pose_landmarker_lite.task` | ~8 MB |

> **Only include models you actually use.** Face-only tracking adds ~5 MB. All three models together add ~25 MB to your APK.

### Option A: Gradle Download Task

Add download tasks directly to your module's `build.gradle.kts`:

```kotlin
val downloadFaceLandmarkerModel by tasks.registering {
    val modelDir = layout.projectDirectory.dir("src/main/assets/models")
    val modelFile = modelDir.file("face_landmarker_v2_with_blendshapes.task")

    outputs.file(modelFile)
    doLast {
        modelDir.asFile.mkdirs()
        if (!modelFile.asFile.exists()) {
            val url = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
            logger.lifecycle("Downloading MediaPipe face landmarker model...")
            ant.invokeMethod("get", mapOf("src" to url, "dest" to modelFile.asFile))
        }
    }
}

// Wire to preBuild — only include the models you need
tasks.named("preBuild") {
    dependsOn(downloadFaceLandmarkerModel)
}
```

### Option B: Manual Download

```bash
mkdir -p app/src/main/assets/models

# Face tracking
curl -L -o app/src/main/assets/models/face_landmarker_v2_with_blendshapes.task \
  "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"

# Hand tracking
curl -L -o app/src/main/assets/models/hand_landmarker.task \
  "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"

# Body tracking
curl -L -o app/src/main/assets/models/pose_landmarker_lite.task \
  "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
```

Add `**/src/main/assets/models/*.task` to your `.gitignore`.

<details>
<summary>Gradle Plugin (coming after first release)</summary>

> **Not yet available.** The Gradle plugin `com.anotherball.facelink` exists in the codebase but has not been published to the Gradle Plugin Portal yet. It will be published with the first tagged release. Until then, use the manual setup options above.

Once published, the plugin will automate model downloads:

```kotlin
plugins {
    id("com.anotherball.facelink")
}

faceLink {
    faceEnabled.set(true)   // default: true  (~5 MB)
    handEnabled.set(true)   // default: true  (~12 MB)
    bodyEnabled.set(false)  // default: false (~8 MB)
    precision.set("float16") // default: "float16"
}
```

The plugin will:
- Download only the models you enable
- Skip re-download when models already exist (with SHA-256 checksum verification)
- Wire downloads to `preBuild` so they run automatically
- Generate a `.gitignore` in the model output directory

</details>

### APK Size Impact

All three models together add approximately **25 MB** to your APK. Strategies to manage this:

- **Include only the models you need.** Face-only tracking adds ~5 MB, which is acceptable for most apps.
- **Use Android App Bundle (AAB).** When publishing to the Play Store, AAB format ensures users only download assets relevant to their device. Model files in `assets/` are included in the base module by default.
- **Consider on-demand delivery.** For apps where tracking is an optional feature, you can use [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery) to download models at runtime rather than bundling them in the initial install.

### Model Version Compatibility

The current library version is tested with MediaPipe Tasks Vision **0.10.33** and the `float16/latest` model variants. Using a different model version or precision variant may cause initialization failures or degraded accuracy.

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

- compileSdk 35 or higher
- Min SDK 24
- Camera permission granted at runtime
- MediaPipe model file in `assets/models/` (see Model Setup above)

## 8. Stream Interaction Demo (INTERACT Mode)

The sample app includes an **INTERACT** demo mode that showcases the `kmp-facelink-interact` module. It simulates live streaming events (chat messages, gifts, follows, raids, super chats, channel point redemptions) and maps them to avatar effect parameters via `DefaultStreamBindings`.

### What It Shows

1. **MockStreamEventSource** generates random stream events at configurable intervals
2. **ActionSystem** with `DefaultStreamBindings` evaluates events and fires action triggers (e.g., gift → `stream.gift.throw`, raid → `stream.raid.shake`)
3. **EffectParameter** models (`ThrowEffect`, `ScreenShake`, `ExpressionOverride`) describe renderer-agnostic effects for your app to visualize

### Switching to Real Stream Sources

Replace `MockStreamEventSource` with a real platform source. No other code changes are needed — all sources implement the same `StreamEventSource` interface.

**Twitch (EventSub WebSocket):**

```kotlin
import io.github.kmpfacelink.interact.twitch.TwitchConfig
import io.github.kmpfacelink.interact.twitch.TwitchEventSubSource

val twitchSource = TwitchEventSubSource(
    config = TwitchConfig(
        accessToken = "your-oauth-token",
        clientId = "your-client-id",
        broadcasterId = "your-broadcaster-user-id",
    ),
)

// Connect and collect events
twitchSource.connect()
twitchSource.events.collect { event -> /* handle */ }
```

**YouTube (Live Chat REST polling):**

```kotlin
import io.github.kmpfacelink.interact.youtube.YouTubeConfig
import io.github.kmpfacelink.interact.youtube.YouTubeLiveChatSource

val youtubeSource = YouTubeLiveChatSource(
    config = YouTubeConfig(
        apiKey = "your-api-key",
        accessToken = "your-oauth-token", // required for auto-detecting active broadcast
    ),
)
```

**Multi-platform aggregation:**

```kotlin
import io.github.kmpfacelink.interact.StreamEventAggregator

val aggregator = StreamEventAggregator()
aggregator.addSource(twitchSource)
aggregator.addSource(youtubeSource)
aggregator.connectAll()
aggregator.events.collect { event -> /* unified event stream */ }
```

## Next Steps

- [API Overview](api-overview.md) for all modules and use cases
- [iOS Quick Start](quick-start-ios.md) for the iOS integration
- [Troubleshooting](troubleshooting.md) for common integration issues
