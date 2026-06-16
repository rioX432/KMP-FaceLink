# Troubleshooting

Common integration issues and their solutions. For platform-specific setup, see the [Android Quick Start](quick-start-android.md) and [iOS Quick Start](quick-start-ios.md). For error recovery patterns, see [API Overview: Error Recovery](api-overview.md#error-recovery).

---

## Android

### 1. Camera permission denied — tracker stays in IDLE

**Symptom:** `tracker.start()` is called but `state` never transitions past `IDLE`, or immediately transitions to `ERROR`. Logcat shows `"No front camera available"` or a permission-related error.

**Cause:** Android 6+ requires runtime camera permission (`android.permission.CAMERA`). If the permission is not granted before calling `start()`, CameraX cannot bind the camera and the tracker either stays idle or errors out.

**Solution:**

1. Declare the permission in `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.CAMERA" />
   ```
2. Request runtime permission **before** calling `tracker.start()`:
   ```kotlin
   val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
       if (granted) {
           lifecycleScope.launch { tracker.start() }
       } else {
           // Show rationale or disable tracking
       }
   }
   launcher.launch(Manifest.permission.CAMERA)
   ```
3. Check the `errorMessage` value for the exact error string if the tracker enters `ERROR`.

---

### 2. MediaPipe model files missing from assets

**Symptom:** `tracker.start()` throws or transitions to `ERROR` with a message like `"Failed to create FaceLandmarker: Cannot read model from assets"`. The app crashes on startup or the tracker never enters `TRACKING`.

**Cause:** The MediaPipe Face Landmarker (and optionally Hand Landmarker / Pose Landmarker) `.task` model files must be bundled in your app's `src/main/assets/models/` directory. Without them, MediaPipe cannot initialize.

**Solution:**

See [Quick Start: Android — Model Setup](quick-start-android.md#2-model-setup) for the full guide including Gradle download tasks, manual download commands, and all three model URLs.

Quick fix for face tracking only:

```bash
mkdir -p app/src/main/assets/models
curl -L -o app/src/main/assets/models/face_landmarker_v2_with_blendshapes.task \
  "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
```

If you use a custom `modelAssetPath` in `FaceTrackerConfig`, ensure the path matches the actual file location relative to `assets/`.

---

### 3. CameraX conflict with other camera libraries

**Symptom:** `tracker.start()` fails with `"CameraX binding failed"` or throws an `IllegalStateException` mentioning camera use cases. This typically happens when another library (ML Kit, ZXing, or a custom camera implementation) already holds the camera.

**Cause:** CameraX binds use cases to a `LifecycleOwner`. If another library has already bound camera use cases to the same lifecycle, CameraX may fail to bind additional use cases or the camera may be locked by the other library entirely.

**Solution:**

- **Remove competing camera bindings.** Only one camera pipeline should be active at a time. If you need barcode scanning alongside face tracking, consider using KMP-FaceLink's shared camera session or switching between features rather than running both simultaneously.
- **Use the correct `LifecycleOwner`.** If you have multiple Activities or Fragments, ensure the `PlatformContext` references the `LifecycleOwner` that owns the screen where tracking is displayed:
  ```kotlin
  // In a Fragment:
  val context = PlatformContext(
      context = requireContext().applicationContext,
      lifecycleOwner = viewLifecycleOwner,  // NOT `this` (Fragment lifecycle)
  )
  ```
- **Wait for the camera to be released.** If the previous screen used the camera, ensure it has fully stopped before starting the tracker on the new screen.

---

### 4. GPU delegate not available — fallback to CPU

**Symptom:** Tracking works but performance is worse than expected. Logcat shows: `"GPU delegate failed"` followed by `"Using CPU delegate (fallback from GPU)"`.

**Cause:** Some devices (especially emulators, older phones, or devices with unsupported GPUs) do not support the OpenGL ES delegate required by MediaPipe's GPU acceleration. KMP-FaceLink automatically falls back from GPU to CPU via `DelegateResolver`, but CPU inference is significantly slower.

**Solution:**

- **This is expected behavior on unsupported devices.** The fallback is automatic and no code change is needed.
- **To force CPU and avoid the fallback log noise**, set the inference delegate explicitly:
  ```kotlin
  val config = FaceTrackerConfig(
      inferenceDelegate = InferenceDelegate.CPU,
  )
  ```
- **For performance-sensitive apps**, check the delegate being used and adjust frame rate expectations or UI feedback (e.g., showing a "reduced performance" indicator).
- **On emulators**, GPU delegation is almost never available. Test GPU performance on physical devices only.

---

### 5. PlatformContext created with wrong lifecycleOwner

**Symptom:** The tracker starts and works initially but stops receiving frames when the Activity is recreated (e.g., screen rotation), or the camera freezes after navigating away and returning.

**Cause:** `PlatformContext` takes a `LifecycleOwner` that CameraX uses to bind camera use cases. If you pass `applicationContext` as both the context and lifecycle owner, or pass a detached lifecycle owner, CameraX cannot manage the camera lifecycle correctly.

**Solution:**

- **In an Activity**, pass `this` as the `lifecycleOwner`:
  ```kotlin
  val context = PlatformContext(
      context = applicationContext,
      lifecycleOwner = this,  // Activity implements LifecycleOwner
  )
  ```
- **In a Fragment**, pass `viewLifecycleOwner`:
  ```kotlin
  val context = PlatformContext(
      context = requireContext().applicationContext,
      lifecycleOwner = viewLifecycleOwner,
  )
  ```
- **Do not use `applicationContext` as lifecycleOwner.** `Application` does not implement `LifecycleOwner` — this will fail at compile time. However, wrapping it in a custom lifecycle owner with no real lifecycle events is a common mistake that leads to the camera never unbinding.
- **With Jetpack Compose**, use `LocalLifecycleOwner.current`:
  ```kotlin
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = remember(lifecycleOwner) {
      PlatformContext(
          context = applicationContext,
          lifecycleOwner = lifecycleOwner,
      )
  }
  ```

---

## iOS

### 6. NSCameraUsageDescription missing — crash on launch

**Symptom:** The app crashes immediately when `tracker.start()` is called with no meaningful stack trace, or the app terminates with a console message about a missing usage description.

**Cause:** iOS requires all apps that access the camera to include an `NSCameraUsageDescription` entry in `Info.plist`. Without it, the OS terminates the app when it tries to access the camera — this is not an exception that can be caught.

**Solution:**

Add the key to `Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>Face tracking requires camera access</string>
```

If using XcodeGen (`project.yml`), add it under `info.properties`:

```yaml
info:
  properties:
    NSCameraUsageDescription: "Face tracking requires camera access"
```

After modifying `project.yml`, regenerate the project:

```bash
xcodegen generate
```

---

### 7. Device without TrueDepth camera — face tracking unavailable

**Symptom:** `tracker.start()` transitions to `ERROR` with `"ARFaceTracking is not supported on this device"`. This happens on iPhone SE, iPad (non-Pro models), or the iOS Simulator.

**Cause:** ARKit face tracking requires a TrueDepth front camera (Face ID hardware). Devices without this camera cannot perform face tracking. This is a **fatal error** — retrying `start()` will not help on the same device.

**Solution:**

- **Check device capability before creating the tracker:**
  ```swift
  import ARKit
  
  if ARFaceTrackingConfiguration.isSupported {
      let tracker = FaceTrackerFactoryKt.createFaceTracker(
          platformContext: PlatformContext(),
          config: FaceTrackerConfig()
      )
      // proceed with tracking
  } else {
      // Show unsupported device message
  }
  ```
- **Hand and body tracking do NOT require TrueDepth.** They use the Vision framework with a standard camera via `AVCaptureSession` and work on all iOS 17+ devices.
- **Add `arkit` to `UIRequiredDeviceCapabilities`** in `Info.plist` if your app requires face tracking to function — this prevents the app from being installed on unsupported devices:
  ```xml
  <key>UIRequiredDeviceCapabilities</key>
  <array>
      <string>arkit</string>
      <string>front-facing-camera</string>
  </array>
  ```

---

### 8. ARKit session interrupted (phone call, Siri, multitasking)

**Symptom:** Face tracking was working but suddenly stops delivering data. The `state` may transition to `ERROR` or remain at `TRACKING` while `trackingData` stops emitting new frames. This happens when the user receives a phone call, activates Siri, or switches to another app.

**Cause:** ARKit sessions are automatically interrupted by the OS when the camera is needed by another process or the app goes to the background. When the interruption ends, ARKit may or may not resume automatically depending on the iOS version and interruption type.

**Solution:**

- **Observe `state` and re-start when the interruption ends:**
  ```swift
  for await state in tracker.state {
      switch state {
      case .error:
          // ARKit interruption may cause an error — retry after a short delay
          try? await Task.sleep(nanoseconds: 1_000_000_000)
          try? await tracker.start()
      case .tracking:
          break  // Back to normal
      default:
          break
      }
  }
  ```
- **Handle app lifecycle events** to explicitly stop and restart:
  ```swift
  .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
      viewModel.stopTracking()
  }
  .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
      viewModel.startTracking()
  }
  ```
- **Reset smoothing** after an interruption to avoid stale filter state from before the gap:
  ```swift
  tracker.resetSmoothing()
  ```

---

### 9. Task not cancelled before stop() — camera lock

**Symptom:** After calling `tracker.stop()`, the camera LED stays on, or a new `tracker.start()` call fails with a camera-in-use error. Other apps also cannot access the camera.

**Cause:** On iOS, you must cancel the Swift `Task` collecting `trackingData` or `state` **before** calling `stop()`. If the collection Task is still running when `stop()` tears down the session, the `for await` loop holds a reference to the session and prevents the camera from being fully released.

**Solution:**

Always follow this order: **cancel Task -> stop() -> release() in deinit**.

```swift
func stopTracking() {
    // 1. Cancel the collection Task FIRST
    observeTask?.cancel()
    observeTask = nil

    // 2. Then stop the tracker
    Task { try? await tracker.stop() }
}

deinit {
    observeTask?.cancel()
    tracker?.release()
}
```

See the [iOS Lifecycle Management](quick-start-ios.md#ios-lifecycle-management) section for the full pattern with code examples.

---

### 10. SKIE async interop pitfalls

**Symptom:** Various unexpected behaviors when using KMP-FaceLink from Swift: functions named `initXxx` are not found, `for await` does not compile, or async calls behave differently than expected.

**Cause:** SKIE bridges Kotlin coroutines and Flows to Swift async/await, but there are naming and behavioral differences to be aware of.

**Solution:**

- **`init`-prefixed functions are renamed.** Kotlin/Native adds a `do` prefix to Kotlin functions starting with `init` (e.g., `initKoin()` becomes `doInitKoin()`). However, SKIE generates its own Swift-native async extension without the prefix. Check both names if a function is not found.
- **`Flow` becomes `AsyncSequence`.** Use `for await` to collect:
  ```swift
  for await data in tracker.trackingData {
      // handle data
  }
  ```
  If `for await` does not compile, ensure SKIE is properly configured in the Gradle build.
- **Suspend functions become async.** Call with `try await`:
  ```swift
  try await tracker.start()
  ```
- **Error types differ.** Kotlin exceptions are bridged as `NSError` subclasses. Catch with `do/catch` in Swift, not Kotlin exception types:
  ```swift
  do {
      try await tracker.start()
  } catch {
      print("Start failed: \(error.localizedDescription)")
  }
  ```

---

## Common (Both Platforms)

### 11. TrackingState.ERROR with no error message

**Symptom:** `state` transitions to `ERROR` but `errorMessage.value` is `null` or an empty string.

**Cause:** Some platform-level errors (particularly on iOS with ARKit) may not provide a descriptive error string. Additionally, `errorMessage` is not cleared on retry — after a successful `start()` following an error, `errorMessage` still contains the previous error text while `state` is `TRACKING`.

**Solution:**

- **Always use `state` to determine the current condition**, not `errorMessage`:
  ```kotlin
  tracker.state.collect { state ->
      when (state) {
          TrackingState.ERROR -> {
              val message = tracker.errorMessage.value ?: "Unknown error"
              showError(message)
          }
          TrackingState.TRACKING -> hideError()
          else -> {}
      }
  }
  ```
- **Log the full error** for debugging. On Android, check Logcat with tag `FaceLandmarkerFactory`, `DelegateResolver`, or `MediaPipeFaceTracker`. On iOS, check the Xcode console for ARKit session errors.
- **Enable FaceLinkLogger** if available, to capture internal SDK log output for diagnosis.

---

### 12. Flow not collected — tracker appears to do nothing

**Symptom:** `tracker.start()` is called and returns without error, but nothing happens. No tracking data arrives, no state changes are observed, and the app appears frozen or idle.

**Cause:** Kotlin `Flow` is cold — `trackingData` and `state` only emit values when actively collected. If no coroutine is collecting the flow, frames are processed but silently dropped. On iOS with SKIE, the same applies to `AsyncSequence` — you must iterate with `for await`.

**Solution:**

- **Always collect both `trackingData` and `state`** in active coroutine scopes:
  ```kotlin
  lifecycleScope.launch {
      tracker.start()
      tracker.trackingData.collect { data ->
          // Process tracking data
      }
  }
  ```
- **On iOS**, use `.task` or create explicit `Task` instances:
  ```swift
  .task {
      try? await tracker.start()
      for await data in tracker.trackingData {
          // Process tracking data
      }
  }
  ```
- **Check that the coroutine scope is still active.** If `lifecycleScope` is tied to a destroyed Activity, collection stops silently. With Compose, use `LaunchedEffect`:
  ```kotlin
  LaunchedEffect(tracker) {
      tracker.start()
      tracker.trackingData.collect { data -> /* ... */ }
  }
  ```
- Note that `trackingData` uses `SharedFlow` with `replay = 1` — new collectors receive the most recent frame immediately if one exists.

---

### 13. release() called but state never reaches RELEASED

**Symptom:** After calling `tracker.release()`, checking `state.value` still shows the previous state (e.g., `TRACKING` or `STOPPED`) instead of `RELEASED`.

**Cause:** `release()` is a synchronous method that sets an internal atomic flag. However, if you are observing `state` in a separate coroutine, there may be a timing gap between the `release()` call and the state flow emission. Additionally, if you check `state.value` immediately in the same synchronous block, the state update may not have propagated yet.

**Solution:**

- **Do not poll `state.value` synchronously after `release()`.** Instead, observe the flow:
  ```kotlin
  lifecycleScope.launch {
      tracker.state.collect { state ->
          if (state == TrackingState.RELEASED) {
              // Cleanup complete
          }
      }
  }
  tracker.release()
  ```
- **`release()` is always safe to call** from any state and can be called multiple times without error.
- **After `release()`, create a new tracker instance** if you need to resume tracking. The released tracker cannot be reused — all method calls will throw `IllegalStateException`.

---

### 14. Repeated start/stop cycles causing memory growth

**Symptom:** Memory usage increases each time the tracker is started and stopped, eventually leading to an `OutOfMemoryError` (Android) or app termination (iOS).

**Cause:** Each `start()` allocates native resources (camera session, MediaPipe pipeline on Android, ARKit session on iOS). If `stop()` is not called properly, or if `release()` is never called when the tracker is no longer needed, these resources accumulate.

On iOS, the most common cause is not calling `release()` in `deinit`, or creating new tracker instances without releasing old ones.

**Solution:**

- **Always follow the full lifecycle:** `start()` -> `stop()` -> `release()`.
- **Call `release()` when the tracker is no longer needed** (e.g., when leaving the tracking screen):
  ```kotlin
  override fun onDestroy() {
      super.onDestroy()
      tracker.release()
  }
  ```
- **Reuse a single tracker instance** instead of creating a new one for each start/stop cycle. The tracker supports multiple `start()` / `stop()` cycles without recreating it.
- **On iOS**, ensure `release()` is called in `deinit`:
  ```swift
  deinit {
      observeTask?.cancel()
      tracker?.release()
  }
  ```
- **Profile memory** with Android Studio Profiler or Xcode Instruments to identify whether the leak is in tracker resources or your own code.

---

### 15. UI updates on wrong thread — crash or no updates

**Symptom:** The app crashes with `"Only the original thread that created a view hierarchy can touch its views"` (Android) or `"Publishing changes from background threads is not allowed"` (iOS/SwiftUI). Alternatively, UI updates are silently dropped and the display never updates.

**Cause:** All tracker flows (`trackingData`, `state`, `errorMessage`) emit on **platform-specific background threads** — the CameraX executor thread on Android and the ARKit/AVCaptureSession delegate thread on iOS. Touching UI components directly inside `collect {}` or `for await` without dispatching to the main thread causes crashes or silent failures.

**Solution:**

**Kotlin (Android):**

```kotlin
// Option 1: withContext
lifecycleScope.launch {
    tracker.trackingData.collect { data ->
        val params = mapper.map(data)  // OK on background thread
        withContext(Dispatchers.Main) {
            updateUi(params)  // UI update on main thread
        }
    }
}

// Option 2: stateIn (recommended for Compose)
val uiState = tracker.trackingData
    .map { mapper.map(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
// collectAsState() in Compose observes on Main automatically
```

**Swift (iOS):**

```swift
// Option 1: @MainActor context (recommended)
// When the Task is created on a @MainActor context (e.g., inside a @MainActor ViewModel),
// SKIE's for-await automatically hops back to the main actor per iteration.
@MainActor
class TrackingViewModel: ObservableObject {
    func startObserving() {
        Task {
            for await data in tracker.trackingData {
                self.blendShapes = data.blendShapes  // Already on MainActor
            }
        }
    }
}

// Option 2: Explicit MainActor.run (for non-@MainActor contexts)
Task.detached { [weak self] in
    for await data in tracker.trackingData {
        await MainActor.run {
            self?.updateUi(data)
        }
    }
}
```

See [API Overview: Threading Model](api-overview.md#threading-model) for the full threading contract.

---

## Still Stuck?

If your issue is not covered here:

1. **Check the error message.** Collect `tracker.errorMessage` and `tracker.state` — the error string often contains the root cause.
2. **Check platform logs.** Android: Logcat with tags `MediaPipeFaceTracker`, `DelegateResolver`, `CameraXManager`. iOS: Xcode console for ARKit/Vision errors.
3. **Review the sample apps.** The `androidApp/` and `iosApp/` directories contain working integration examples.
4. **Open a GitHub issue** at [KMP-FaceLink Issues](https://github.com/AnotherBall/KMP-FaceLink/issues) with:
   - Platform and OS version
   - Device model
   - Full error message / stack trace
   - Minimal reproduction code
