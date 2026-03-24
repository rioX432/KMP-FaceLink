# Android Plugin Setup

Place the KMP-FaceLink AAR file here:

```
Plugins/Android/kmp-facelink-release.aar
```

## Building the AAR

From the KMP-FaceLink project root:

```bash
./gradlew :kmp-facelink:assembleRelease
```

The AAR will be at:
`kmp-facelink/build/outputs/aar/kmp-facelink-release.aar`

## Unity Facade

The AAR must include a thin Java facade class that bridges Unity's polling
model with KMP-FaceLink's Flow-based streaming API:

```
io.github.kmpfacelink.unity.FaceLinkUnityFacade
```

This facade:
1. Creates FaceTracker/HandTracker instances
2. Collects Flow emissions on a background coroutine
3. Buffers the latest frame data in thread-safe fields
4. Exposes getter methods callable from Unity via AndroidJavaObject

See `unity-plugin/Documentation~/android-facade.md` for the full API spec.
