# Legacy Paired-Capture Research Tool

Records ARKit face blendshape output and front-camera RGB frames simultaneously, producing paired datasets for ARKit-to-MediaPipe comparison and calibration research.

This folder is retained only to reproduce historical paired-capture analyses. It is not the current
recording application and must not be used as an SDK quality or product-readiness gate. New guided
recording uses the shared internal `kmp-facelink-capture` module through `captureApp` (Android) and
`captureIosApp` (iOS). Any new physical recording, participant involvement, or biometric-data use
requires the separate human authorization and privacy protocol.

## Components

| Component | Description |
|---|---|
| `CaptureApp/` | Legacy iOS paired-capture app retained for historical reproduction |
| `process_captures.py` | Offline MediaPipe processing of captured RGB frames |
| `benchmark.py` | Per-blendshape agreement benchmark and difference report |
| `paired_timeline.py` | Slide-ready paired timelines and JSON summaries for one session |
| `CAPTURE_RUNBOOK.md` | Archived protocol for the legacy app; not current capture guidance |
| `optimize.py` | Automated parameter sweep for BlendShapeEnhancer defaults |
| `train_predictor.py` | Train TFLite neural blendshape predictor (landmarks -> 52 ARKit shapes) |
| `requirements.txt` | Python dependencies |

## Historical Reproduction Only

### 1. Build the iOS Capture App

Requires Xcode 16+, XcodeGen, and iOS 16+ for this legacy target.

```bash
cd CaptureApp
xcodegen generate
open CaptureApp.xcodeproj
```

Set your signing team in Xcode, then build and run on an iPhone or iPad that
satisfies the app's declared A12-or-later capability. On iOS/iPadOS 14 and
later, ARKit face tracking supports Apple Neural Engine devices; the app's
runtime `ARFaceTrackingConfiguration.isSupported` check is authoritative.
The current guided capture workflow is not documented by this legacy runbook.

### 2. Capture Sessions

1. Launch the app on device
2. Enter a non-identifying **participant pseudonym** and lighting notes; never enter a real name
3. Tap **Track** to start ARKit face tracking
4. Tap **Record** to begin capturing frames
5. Record one protocol clip, then tap **Stop** and **Export**
6. Repeat for the next clip (do not record all clips as one session)

The app samples at up to 30 fps and keeps JPEG-compressed frames in memory until export.
Export each short clip before starting the next one.

Sessions are saved to the app's Documents directory under `captures/session_{timestamp}/`.

### 3. Transfer Session Data

Use Xcode's Devices and Simulators window or the Files app to transfer session directories to your Mac:

```
captures/session_20260521_143022/
├── metadata.json           # Device model, iOS version, notes
├── arkit_blendshapes.csv   # timestamp + 52 blendshape columns
├── frames/
│   ├── 000000.jpg          # RGB frames
│   ├── 000001.jpg
│   └── ...
└── timestamps.csv          # frame_index → timestamp
```

### 4. Run MediaPipe Processing

```bash
# Install dependencies
pip install -r requirements.txt

# Process a session
python process_captures.py path/to/session_20260521_143022/ \
  --delegate cpu \
  --model ../../androidApp/src/main/assets/models/face_landmarker_v2_with_blendshapes.task
```

This produces `mediapipe_blendshapes.csv`, `mediapipe_landmarks.npz`, and
`mediapipe_processing.json` alongside `arkit_blendshapes.csv` in the session
directory. The compressed NPZ contains `landmarks` as float32 `[N, 478, 3]`,
`facial_transformation_matrices` as float32 `[N, 4, 4]` in MediaPipe's emitted
layout (without transposition), plus aligned `frame_indices` (int32) and
`timestamps` (float64) for the same successfully processed frames as the
MediaPipe CSV. The JSON sidecar records the MediaPipe and Python versions,
delegate, model path and SHA-256, processed/skipped frame counts, artifact
SHA-256, and the landmark and transformation-matrix shapes and dtypes.
Offline processing defaults to the CPU delegate for deterministic runs and macOS compatibility.
Pass `--delegate gpu` only when that host's MediaPipe GPU delegate has been verified.

### 5. Run Agreement Benchmark

After processing one or more sessions, run the benchmark to compare paired ARKit and MediaPipe output:

```bash
# Basic report (generates reports/agreement_report.md)
python benchmark.py --captures captures/ --output reports/

# With scatter plots (generates reports/plots/*.png)
python benchmark.py --captures captures/ --output reports/ --plots
```

The benchmark:
- Loads all `session_*` directories from the captures folder
- Aligns ARKit and MediaPipe frames by nearest timestamp (16ms tolerance)
- Computes per-blendshape mean absolute difference, RMSE, Pearson correlation, max difference, and range ratio
- Assigns descriptive review bands for triage; these are not validated accuracy grades
- Outputs a ranked Markdown agreement report and optional scatter plots

ARKit is a reference signal in this workflow, not ground truth. The app pairs an
ARKit live result with RGB saved from the same `didUpdate` callback; MediaPipe is
then run on that saved RGB. Paired measurements can show agreement,
disagreement, response range, and timing, but this does not mean that one
identical image was fed into both estimators. The workflow cannot by itself
establish either system's physical accuracy or explain why the systems differ.

For a slide-ready time series from one processed session, select up to four
blendshapes and optionally mark explicit analysis windows:

```bash
python paired_timeline.py captures/session_20260817_001122 \
  --blendshapes cheekPuff mouthPucker \
  --window neutral:0:2 \
  --window puff_1:2:5 \
  --window puff_2:7:10 \
  --output reports/cheek_puff
```

This writes `paired_timeline.png`, an editable `paired_timeline.svg`, and
`paired_summary.json`. Window times are seconds from the first aligned pair and
are analyst annotations, not automatically detected expression boundaries.

## Withdrawn optimization guidance

`optimize.py` and `train_predictor.py` are retained as historical prototypes only. Their previous
README recipes have been removed because they used ARKit values as optimization/teacher targets,
split correlated frames rather than independent participant/session units in the ordinary path,
and attached arbitrary success thresholds. Their output must not update SDK defaults, qualify a
model, or support an accuracy/generalization claim. A future reference-distillation experiment
requires a preregistered independent participant/session split, explicit missing-data accounting,
and review of the private biometric-data protocol.

The old fixed “minimum dataset” table is also withdrawn. Participant/session counts and acceptance
thresholds must be derived from the product's tolerated false activation/miss costs after a pilot;
frame count is not an independent sample count.

## Output Format

### arkit_blendshapes.csv
```csv
timestamp,browDownLeft,browDownRight,browInnerUp,...,tongueOut
1716300000.123456,0.045000,0.042000,0.001000,...,0.000000
```

### mediapipe_blendshapes.csv
```csv
frame_index,timestamp,_neutral,browDownLeft,browDownRight,...,mouthPucker
0,1716300000.123456,0.012345,0.038000,0.035000,...,0.104000
```

`tongueOut` is intentionally absent from this MediaPipe example. The benchmark
reports it as ARKit-only when it is present in the captured ARKit CSV.

### timestamps.csv
```csv
frame_index,timestamp
0,1716300000.123456
1,1716300000.156789
```

### metadata.json
```json
{
    "app_version": "1.0.0",
    "capture_date": "2026-05-21T14:30:22Z",
    "device_model": "iPhone15,2",
    "frame_count": 1024,
    "ios_version": "18.5",
    "notes": "Overhead fluorescent lighting, afternoon",
    "subject_name": "participant_01"
}
```

`subject_name` is a legacy schema key. Its value must be a non-identifying pseudonym. The legacy
exporter no longer writes the user-assigned device name.
