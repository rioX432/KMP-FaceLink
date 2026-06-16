# Publishing

This document describes how to publish KMP-FaceLink to Maven Central (Android/JVM/Wasm) and GitHub Releases + SPM (iOS).

## Architecture Overview

The publishing pipeline is fully automated via GitHub Actions (`.github/workflows/release.yml`). Pushing a `v*` tag triggers:

1. **Maven Central** — all 14 modules are published to Sonatype OSSRH, then the staging repository is closed and released.
2. **XCFramework builds** — each iOS-capable module is built into a release XCFramework and zipped.
3. **GitHub Release** — XCFramework zips are attached to the release with SHA-256 checksums.
4. **Package.swift update** — `scripts/update-package-swift.py` rewrites checksums in `Package.swift` so SPM consumers get the correct binaries.
5. **Dokka docs** — API documentation is generated and deployed to GitHub Pages.

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

The BOM (`kmp-facelink-bom`) is a `java-platform` module that manages versions for all 13 library modules. Android-only consumers can import the BOM via `platform()` and omit version numbers on individual modules. **KMP consumers cannot use `platform()` in `commonMain.dependencies {}`** ([KT-58759](https://youtrack.jetbrains.com/issue/KT-58759)) and should use a version catalog with explicit `version.ref` instead. See [Quick Start Integration](quick-start-integration.md) for usage examples.

## First-Time Setup Checklist

Before the first release, the repository owner must complete these one-time steps:

### 1. Sonatype OSSRH Account

- [ ] Register at [Sonatype OSSRH](https://s01.oss.sonatype.org/) (or [central.sonatype.com](https://central.sonatype.com/) for the new Central Portal)
- [ ] Verify the `com.anotherball` namespace via DNS TXT record on `anotherball.com` (Central Portal flow)
- [ ] Wait for namespace verification

### 2. GPG Signing Key

- [ ] Generate a GPG key: `gpg --full-generate-key` (RSA 4096, no expiry or long expiry)
- [ ] Export the key ID: `gpg --list-keys --keyid-format long`
- [ ] Export the private key in ASCII armor: `gpg --export-secret-keys --armor <KEY_ID>`
- [ ] Publish the public key to a keyserver: `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`

### 3. GitHub Secrets

Add these secrets to the repository (Settings > Secrets and variables > Actions):

| Secret | Description |
|--------|-------------|
| `OSSRH_USERNAME` | Sonatype OSSRH username |
| `OSSRH_PASSWORD` | Sonatype OSSRH password (or token) |
| `GPG_KEY_ID` | Last 8 characters of the GPG key fingerprint |
| `GPG_KEY` | Full ASCII-armored private key (output of `gpg --export-secret-keys --armor`) |
| `GPG_PASSPHRASE` | Passphrase for the GPG key |

### 4. GitHub Pages

- [ ] Enable GitHub Pages in repository settings (Settings > Pages > Source: GitHub Actions)

## Releasing a Version

### 1. Update the version

Edit `gradle.properties`:

```properties
VERSION_NAME=X.Y.Z
```

### 2. Update CHANGELOG.md

Add a section for the new version with a summary of changes.

### 3. Update docs/migration.md

If this release contains breaking changes, deprecated API removals, or new requirements:
- Add a migration section for this version in `docs/migration.md`
- Verify the deprecated API removal schedule is accurate

### 4. Commit and tag

```bash
git add gradle.properties CHANGELOG.md docs/migration.md
git commit -m "Release vX.Y.Z"
git tag vX.Y.Z
git push origin master --tags
```

### 5. Monitor the release

The `vX.Y.Z` tag push triggers the Release workflow. Monitor progress at:
`https://github.com/rioX432/KMP-FaceLink/actions/workflows/release.yml`

The workflow will:
1. Publish all modules to Maven Central staging, then close and release the staging repository
2. Build XCFrameworks for each iOS module
3. Create a GitHub Release with XCFramework zips and checksums
4. Update `Package.swift` checksums and commit to `master` automatically
5. Deploy Dokka documentation to GitHub Pages

### 6. Verify

- [ ] Check [Maven Central](https://central.sonatype.com/namespace/com.anotherball) for the published artifacts (may take 15-30 minutes to sync)
- [ ] Check the GitHub Release page for XCFramework downloads
- [ ] Verify `Package.swift` checksums were updated by the release workflow (check the auto-commit on `master`)
- [ ] Verify SPM resolution by adding the package in a fresh Xcode project
- [ ] Check API docs at https://rioX432.github.io/KMP-FaceLink/

## Local Development

To test the publishing configuration locally without actually publishing:

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
| `VERSION_NAME` | `0.2.0` | Artifact version |
| `POM_URL` | GitHub repo URL | POM project URL |
| `POM_SCM_URL` | GitHub repo URL | Source control URL |
| `POM_SCM_CONNECTION` | `scm:git:git://...` | Read-only SCM connection |
| `POM_SCM_DEV_CONNECTION` | `scm:git:ssh://...` | Developer SCM connection |
| `POM_LICENCE_NAME` | `MIT License` | License name |
| `POM_LICENCE_URL` | MIT URL | License URL |
| `POM_DEVELOPER_ID` | `rioX432` | Developer identifier |
| `POM_DEVELOPER_NAME` | `rioX432` | Developer display name |

### Environment Variables (CI)

| Variable | Source | Purpose |
|----------|--------|---------|
| `OSSRH_USERNAME` | GitHub Secret | Sonatype authentication |
| `OSSRH_PASSWORD` | GitHub Secret | Sonatype authentication |
| `GPG_KEY_ID` | GitHub Secret | Signing key identifier |
| `GPG_KEY` | GitHub Secret | ASCII-armored private key |
| `GPG_PASSPHRASE` | GitHub Secret | Key passphrase |

## Troubleshooting

### Staging repository fails to close

Sonatype's validation rules may reject the staging repository. Common reasons:
- Missing POM metadata (description, SCM, license, developer) -- already configured in `build.gradle.kts`
- Missing source or javadoc JARs -- javadoc JAR is configured; KMP generates source JARs automatically
- Signature verification failure -- ensure the GPG public key is published to a keyserver

Check the staging repository at https://s01.oss.sonatype.org/#stagingRepositories for detailed error messages.

### Package.swift Checksum Management

`Package.swift` in the repository contains `CHECKSUM_PLACEHOLDER` values for all binary XCFramework targets. **This is intentional.** The placeholders are replaced with real SHA-256 checksums during the release workflow.

**How it works:**

1. The release workflow builds each XCFramework and zips it
2. `sha256sum` computes checksums for all zip files
3. `scripts/update-package-swift.py` replaces placeholders (or previous checksums) and the version string in `Package.swift`
4. The workflow commits the updated `Package.swift` back to `master`

The script handles both fresh placeholders and re-runs (already-replaced checksums), so it is idempotent.

**Modules covered** (9 XCFrameworks):

`KMPFaceLink`, `KMPFaceLinkAvatar`, `KMPFaceLinkActions`, `KMPFaceLinkVoice`, `KMPFaceLinkLlm`, `KMPFaceLinkPipeline`, `KMPFaceLinkNetworking`, `KMPFaceLinkVMC`, `KMPFaceLinkIFM`

**Manual checksum update (for local testing):**

```bash
# Build the XCFramework
./gradlew :kmp-facelink:assembleKMPFaceLinkXCFramework

# Zip and compute checksum
cd kmp-facelink/build/XCFrameworks/release
zip -r KMPFaceLink.xcframework.zip KMPFaceLink.xcframework
shasum -a 256 KMPFaceLink.xcframework.zip

# Replace the checksum in Package.swift manually, or use the script:
echo "<checksum>  KMPFaceLink.xcframework.zip" > checksums.txt
python3 scripts/update-package-swift.py Package.swift checksums.txt vX.Y.Z-local
```

**If the auto-commit fails:**

The workflow uses `github-actions[bot]` to push the commit. If branch protection rules block the push, download the `Package-swift` artifact from the workflow run and commit it manually.
