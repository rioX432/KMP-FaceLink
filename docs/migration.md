# Migration Guide

This document tracks breaking changes between versions of KMP-FaceLink. Use it when upgrading to identify what needs to change in your code.

**How to use:** Find your current version and the target version below, then apply each section's changes in order.

---

<!-- TEMPLATE: Copy the block below for each new release with breaking changes -->

<!--
## X.Y.Z → X.Y+1.Z

### Breaking Changes

- **[module]** Description of what changed and why
  ```kotlin
  // Before
  oldApi()
  // After
  newApi()
  ```

### Deprecated APIs Removed

- `DeprecatedClass` — use `NewClass` instead (deprecated in X.Y.Z)

### New Requirements

- Minimum SDK / OS version changes
- New permissions needed
- New model files or assets required

### Gradle Dependency Changes

```toml
# gradle/libs.versions.toml
[versions]
kmp-facelink = "X.Y+1.Z"
```

```kotlin
// Android-only projects can still use the BOM:
implementation(platform("com.anotherball:kmp-facelink-bom:X.Y+1.Z"))

// New modules added (if any)
implementation("com.anotherball:kmp-facelink-newmodule:X.Y+1.Z")
```

- Modules removed: none

### Notes

- Any other relevant information for this upgrade

-->

## 0.1.0 → 0.2.0

### Breaking Changes

- **[core]** `FaceTrackerConfig` split into `DetectionConfig`, `PowerConfig`, and `PerformanceConfig`
  ```kotlin
  // Before
  FaceTrackerConfig(minDetectionConfidence = 0.5f, maxFaces = 1)

  // After
  FaceTrackerConfig(
      detection = DetectionConfig(minConfidence = 0.5f, maxFaces = 1),
      power = PowerConfig.DEFAULT,
      performance = PerformanceConfig.DEFAULT,
  )
  ```

### Deprecated APIs Removed

- `InferenceDelegate.NNAPI` — use `InferenceDelegate.GPU` or `InferenceDelegate.NPU` instead (deprecated in 0.1.0)
- Legacy smoother factory functions — use `TrackerConfig.createSmoother()` instead (deprecated in 0.1.0)

### New Requirements

- None

### Gradle Dependency Changes

```toml
# gradle/libs.versions.toml — bump the version
[versions]
kmp-facelink = "0.2.0"
```

```kotlin
// Android-only projects can still use the BOM:
implementation(platform("com.anotherball:kmp-facelink-bom:0.2.0"))
```

- No new modules added
- No modules removed

### Notes

- Consumer ProGuard rules are now bundled in library modules. If you have manual ProGuard rules for KMP-FaceLink dependencies, you can remove them.

---

## General Migration Tips

1. **Update the version first** — bump the version in `gradle/libs.versions.toml` (or the BOM for Android-only projects) before anything else. This ensures all modules use compatible versions. Note: `platform()` BOM imports do not work in KMP `commonMain.dependencies {}` ([KT-58759](https://youtrack.jetbrains.com/issue/KT-58759)); use a version catalog with `version.ref` instead.
   ```toml
   # gradle/libs.versions.toml
   [versions]
   kmp-facelink = "NEW_VERSION"
   ```
2. **Check deprecated API warnings** — build your project with the current version and resolve all deprecation warnings before upgrading. Deprecated APIs may be removed in the next release.
3. **Run tests after upgrading** — verify your tracking, avatar, and pipeline integrations still work as expected.
4. **Review the [CHANGELOG](../CHANGELOG.md)** — the migration guide covers breaking changes only. See the changelog for the full list of additions, fixes, and improvements.
