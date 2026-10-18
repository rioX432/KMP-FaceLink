# Publishing

This document describes how KMP-FaceLink is published to **GitHub Packages** (Android/JVM/Wasm Maven artifacts) and **GitHub Releases + SPM** (iOS XCFrameworks).

> Maven Central, Gradle Plugin Portal, and GPG signing were intentionally removed. GitHub Packages is the sole Maven channel; SPM via `Package.swift` is the sole iOS channel.

## Architecture Overview

Publishing is fully automated via GitHub Actions (`.github/workflows/publish-github-packages.yml`). **Every merge to `master`** triggers:

1. **Version resolution** — `0.<minor>.<UTC YYYYMMDDHHMM>` (MAJOR.MINOR from `VERSION_NAME`, patch replaced by a UTC timestamp). Each merge yields a unique, immutable version, so there are no `-SNAPSHOT` artifacts and no 409 conflicts.
2. **GitHub Packages** — all publishable modules are published to `https://maven.pkg.github.com/AnotherBall/KMP-FaceLink`.
3. **XCFramework builds** — each iOS-capable module is built into a release XCFramework and zipped.
4. **GitHub Release** — a `v<version>` release is created at the built commit with the XCFramework zips attached.
5. **Package.swift** — `scripts/generate-package-swift.py` regenerates the manifest from the release assets, pointing each binary target at the GitHub REST API asset URL (required because this is a **private** repo — public `releases/download/...` URLs are not resolvable by SwiftPM). The manifest is committed to `master` with `[skip ci]` (so the auto-commit does not re-trigger the workflow), and the `v<version>` tag is force-moved onto that commit so SwiftPM consumers resolving the tag read the correct manifest.

`workflow_dispatch` is also available for manual publishing, optionally with an explicit `version` input (e.g. `0.3.0`) instead of the auto timestamp.

### Published Modules

| Module | Maven Artifact | SPM Product |
|--------|---------------|-------------|
| `kmp-facelink-bom` | `com.anotherball:kmp-facelink-bom` | -- (Maven BOM) |
| `kmp-facelink` | `com.anotherball:kmp-facelink` | `KMPFaceLink` |
| `kmp-facelink-avatar` | `com.anotherball:kmp-facelink-avatar` | `KMPFaceLinkAvatar` |
| `kmp-facelink-actions` | `com.anotherball:kmp-facelink-actions` | `KMPFaceLinkActions` |
| `kmp-facelink-interact` | `com.anotherball:kmp-facelink-interact` | `KMPFaceLinkInteract` |
| `kmp-facelink-compose` | `com.anotherball:kmp-facelink-compose` | -- (not yet exposed via SPM) |
| `kmp-facelink-voice` | `com.anotherball:kmp-facelink-voice` | `KMPFaceLinkVoice` |
| `kmp-facelink-llm` | `com.anotherball:kmp-facelink-llm` | `KMPFaceLinkLlm` |
| `kmp-facelink-pipeline` | `com.anotherball:kmp-facelink-pipeline` | `KMPFaceLinkPipeline` |
| `kmp-facelink-networking` | `com.anotherball:kmp-facelink-networking` | `KMPFaceLinkNetworking` |
| `kmp-facelink-vmc` | `com.anotherball:kmp-facelink-vmc` | `KMPFaceLinkVMC` |
| `kmp-facelink-ifm` | `com.anotherball:kmp-facelink-ifm` | `KMPFaceLinkIFM` |
| `kmp-facelink-stream` | `com.anotherball:kmp-facelink-stream` | -- (JVM only) |
| `kmp-facelink-vts` | `com.anotherball:kmp-facelink-vts` | -- (JVM only) |

The BOM (`kmp-facelink-bom`) is a `java-platform` module managing versions for all library modules. Android-only consumers can import it via `platform()`; **KMP consumers cannot use `platform()` in `commonMain.dependencies {}`** ([KT-58759](https://youtrack.jetbrains.com/issue/KT-58759)) and should use a version catalog with explicit `version.ref`. See [Quick Start Integration](quick-start-integration.md).

## CI Details

- **Triggers**: push to `master`, or manual `workflow_dispatch` (optional explicit `version`).
- **Runner**: GitHub-hosted `macos-15` for the build jobs, with `scripts/setup-whisper-ios.sh`. macOS is required because `kmp-facelink-voice` has an iOS `whisper.cpp` cinterop, so the iOS klib publications cannot be built on Linux. Version resolution and release creation run on `ubuntu-latest`.
- **Auth**: the built-in `GITHUB_TOKEN` (`permissions: packages: write` / `contents: write`), injected explicitly into the Gradle process as `GITHUB_TOKEN` / `GITHUB_ACTOR`.
- **No signing**: GitHub Packages does not require GPG signatures, so no `Sign` tasks run and no `.asc` artifacts are produced.

### Why timestamp versions

GitHub Packages **rejects overwriting an already-published release version** of a Maven artifact (HTTP 409). The `0.<minor>.<UTC timestamp>` scheme guarantees a fresh version on every merge, avoiding both `-SNAPSHOT` mutability and 409 conflicts. To cut a clean named release, run `workflow_dispatch` with an explicit `version` (e.g. `0.3.0`).

## Releasing

### Automatic (default)

Merge a PR to `master`. The workflow publishes `0.<minor>.<timestamp>` to GitHub Packages, builds the XCFrameworks, and creates the matching GitHub Release. No manual steps.

### Manual / named version

1. (Optional) bump `VERSION_NAME` in `gradle.properties` to change the `MAJOR.MINOR` base.
2. Trigger the workflow manually:
   ```bash
   gh workflow run publish-github-packages.yml --ref master -f version=0.3.0
   ```

### Verify

- [ ] Check [Packages](https://github.com/orgs/AnotherBall/packages?repo_name=KMP-FaceLink) for the published Maven artifacts
- [ ] Check the [Releases](https://github.com/AnotherBall/KMP-FaceLink/releases) page for XCFramework downloads
- [ ] Verify the `Update Package.swift for v<version> [skip ci]` commit landed on `master` and the `v<version>` tag points at it
- [ ] Verify SPM resolution by adding the package in a fresh Xcode project (with an authenticated GitHub token, since the repo is private)
- [ ] Check API docs at https://anotherball.github.io/KMP-FaceLink/

## Local Development

Test the publishing configuration locally without publishing to GitHub Packages:

```bash
# Publish all modules to the local Maven repository (~/.m2/repository/)
./gradlew publishToMavenLocal

# Verify the artifacts were created
ls ~/.m2/repository/com/anotherball/kmp-facelink/
```

Then in your consuming project, add `mavenLocal()` to the repositories block:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
```

## Configuration Reference

### Gradle Properties (`gradle.properties`)

| Property | Value | Purpose |
|----------|-------|---------|
| `GROUP` | `com.anotherball` | Maven group ID |
| `VERSION_NAME` | `0.3.0` | `MAJOR.MINOR` base for the timestamp version |
| `POM_URL` | GitHub repo URL | POM project URL |
| `POM_SCM_URL` | GitHub repo URL | Source control URL |
| `POM_SCM_CONNECTION` | `scm:git:git://...` | Read-only SCM connection |
| `POM_SCM_DEV_CONNECTION` | `scm:git:ssh://...` | Developer SCM connection |
| `POM_LICENCE_NAME` | `MIT License` | License name |
| `POM_LICENCE_URL` | MIT URL | License URL |
| `POM_DEVELOPER_ID` | `anotherball` | Developer identifier |
| `POM_DEVELOPER_NAME` | `anotherball` | Developer display name |

### Environment Variables (CI)

| Variable | Source | Purpose |
|----------|--------|---------|
| `GITHUB_TOKEN` | Built-in (`secrets.GITHUB_TOKEN`) | GitHub Packages auth + release creation |
| `GITHUB_ACTOR` | Built-in (`github.actor`) | Publishing username |

## Troubleshooting

### Package.swift Generation

`Package.swift` is regenerated from the GitHub Release assets on every run by `scripts/generate-package-swift.py`.

**How it works:**

1. The workflow builds each XCFramework and zips it, then creates the release with the zips attached
2. `gh api /repos/<repo>/releases/tags/<tag>` is dumped to `release.json`
3. `scripts/generate-package-swift.py Package.swift <version> release.json release-assets` regenerates the manifest: for each framework it reads the REST API asset URL from `release.json` and computes the SHA-256 of the local zip
4. The manifest is committed to `master` with `[skip ci]` and the tag is force-moved onto that commit

Binary target URLs use the API asset form `https://api.github.com/repos/<repo>/releases/assets/<id>.zip` because the repo is **private** (SwiftPM authenticates via token / `.netrc`).

**Frameworks covered** (10 XCFrameworks):

`KMPFaceLink`, `KMPFaceLinkAvatar`, `KMPFaceLinkActions`, `KMPFaceLinkVoice`, `KMPFaceLinkLlm`, `KMPFaceLinkPipeline`, `KMPFaceLinkNetworking`, `KMPFaceLinkVMC`, `KMPFaceLinkIFM`, `KMPFaceLinkInteract`

**If the commit / tag move fails:**

The workflow pushes as `github-actions[bot]`. If branch protection blocks it, the release and its assets are still created; re-run the `create-release` job, or generate `Package.swift` locally with `generate-package-swift.py` against the release JSON, commit it to `master`, and force-move the tag manually.
