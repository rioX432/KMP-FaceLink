# Archived ARKit / MediaPipe Paired-Capture Runbook

This document reproduces the legacy `tools/calibration/CaptureApp` workflow only. It is not the
current KMP-FaceLink guided capture application or a release-quality protocol. Do not use it for a
new recording without the separate participant, biometric-data, and run authorization. Current
apps are `captureApp` (Android) and `captureIosApp` (iOS), backed by `kmp-facelink-capture`.

This runbook records ARKit output and front-camera RGB frames from the same
`ARFrame`, then evaluates output agreement offline. ARKit is a reference signal,
not physical ground truth. The workflow can show response, range, timing, and
agreement differences; it cannot by itself establish either system's accuracy
or the cause of a difference.

## 1. Build the Capture App

Requirements:

- An iPhone or iPad running iOS/iPadOS 16 or later for this legacy app target
- Xcode and XcodeGen
- A development signing team selected in Xcode

Apple documents `ARFaceTrackingConfiguration` as available on Apple Neural
Engine devices on iOS/iPadOS 14 and later. The runtime
`ARFaceTrackingConfiguration.isSupported` check remains the final authority for
the exact device and OS combination.

```bash
cd tools/calibration/CaptureApp
xcodegen generate
open CaptureApp.xcodeproj
```

Select the physical iPhone or iPad as the run destination, set the signing team,
and run the app only under an approved historical-reproduction protocol. Record the exact unsigned
build and signed-device verification receipts for that run; this document does not assert that a
current binary has passed them.

## 2. Fix and Record the Conditions

Keep these conditions constant for the whole session and write them into the
session notes or the measurement log:

- Subject pseudonym (do not use a real name)
- Device model and iOS/iPadOS version
- Capture date and app commit
- Portrait orientation
- Camera-to-face distance (target: 40–60 cm)
- Lighting direction and approximate brightness
- Glasses / mask / facial hair state
- MediaPipe runtime version and inference delegate
- Face Landmarker model SHA-256

Use a stable device position, a frontal neutral pose, and even lighting. This is
a one-subject case study unless more participants are deliberately recruited;
do not generalize the result to all users or devices.

## 3. Record One Clip Per Export

For every clip:

1. Fill in the subject pseudonym and a clip label in **Lighting / session notes**.
2. Tap **Track** and wait for `Normal`.
3. Tap **Record**.
4. Hold a complete neutral expression for at least 2 seconds.
5. Form the target expression, then hold it for 3 seconds after it is complete.
6. Return fully to neutral for at least 2 seconds, repeat the completed
   expression for 3 seconds, then finish with at least 2 seconds of neutral.
7. Tap **Stop**, then **Export**.
8. Confirm the export before starting the next clip.

Do not record all clips into one in-memory session. The app samples up to 30 fps
and stores JPEG-compressed frames until each export.

### Required clips

Record the 15 core clips from the existing cheek-puff recording protocol, plus
`19_tongue_out` so the ARKit-only `tongueOut` schema difference can be verified.
Keep `21_jaw_forward` as a recommended extra clip because it directly tests a
disputed weak shape, but do not treat it as required unless rio elects to add it.

After transferring the app's `captures` directory to the Mac, rename each
session while retaining the `session_` prefix, for example:

```text
session_01_neutral
session_02_cheek_puff
session_19_tongue_out
session_21_jaw_forward
```

Keep an untouched backup of the exported capture directories before processing.
Face RGB data is biometric-sensitive; do not publish raw frames without an
explicit privacy and consent decision.

## 4. Create the Reproducible Processing Environment

This archived offline recipe used the Python MediaPipe `0.10.30` wheel. The current Android source
pins MediaPipe Tasks Vision `0.10.35`, so this is an explicit runtime mismatch and not current
device-parity guidance. Always record the exact runtime and use the exact model asset for the
historical artifact being reproduced.

```bash
uv venv --python 3.12 .venv-calibration
uv pip install --python .venv-calibration/bin/python \
  "mediapipe==0.10.30" pandas matplotlib
```

Record the model hash before processing:

```bash
shasum -a 256 \
  androidApp/src/main/assets/models/face_landmarker_v2_with_blendshapes.task
```

The verified model hash is:

```text
64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff
```

## 5. Process Every Session

From the repository root, use CPU inference as the declared default:

```bash
for session in /path/to/captures/session_*; do
  .venv-calibration/bin/python tools/calibration/process_captures.py \
    "$session" \
    --delegate cpu \
    --model androidApp/src/main/assets/models/face_landmarker_v2_with_blendshapes.task
done
```

On macOS, MediaPipe image preprocessing can initialize graphics services even
with CPU inference. If a sandboxed shell cannot initialize them, run the same
command in a normal Terminal session rather than switching delegates silently.

Check each session for `mediapipe_blendshapes.csv`, `mediapipe_landmarks.npz`,
a nonzero processed-frame count, and zero or explained skipped frames. The NPZ
stores float32 landmarks `[N, 478, 3]`, MediaPipe facial transformation
matrices `[N, 4, 4]` without transposition, and aligned frame indices and
timestamps. Keep the generated `mediapipe_processing.json` sidecar with both
outputs; it records the runtime, delegate, model hash, frame counts, artifact
SHA-256, and the landmark and transformation-matrix shapes.

## 6. Generate the Agreement Report

```bash
.venv-calibration/bin/python tools/calibration/benchmark.py \
  --captures /path/to/captures \
  --output /path/to/reports \
  --plots
```

Expected primary output: `agreement_report.md`.

Verify these report invariants before using any number in slides:

- Only columns present in both systems are numerically compared.
- The schema section lists `tongueOut` as ARKit-only when captured.
- MediaPipe's `_neutral` category is listed as MediaPipe-only metadata/output.
- The report says agreement/difference, never physical accuracy or ground truth.
- Every slide number is copied into `facts-ledger.md` with its subject count,
  frame count, device, OS, runtime, delegate, model hash, and capture conditions.

The paired capture removes the dual-device viewpoint mismatch: ARKit's live
output is associated with the RGB saved from the same `didUpdate` callback, and
only that saved RGB is later passed to MediaPipe. This is not an experiment that
feeds one identical image into both estimators, and it still does not isolate
sensor depth, model architecture, training data, or other causes.
