# Calibration Capture Tool

Records ARKit face blendshape ground truth and front-camera RGB frames simultaneously, producing paired datasets for ARKit-to-MediaPipe parameter calibration.

## Components

| Component | Description |
|---|---|
| `CaptureApp/` | iOS app (SwiftUI + ARKit) for dual recording |
| `process_captures.py` | Offline MediaPipe processing of captured RGB frames |
| `benchmark.py` | Per-blendshape accuracy benchmark and error report |
| `requirements.txt` | Python dependencies |

## Quick Start

### 1. Build the iOS Capture App

Requires Xcode 16+ and [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
cd CaptureApp
xcodegen generate
open CaptureApp.xcodeproj
```

Set your signing team in Xcode, then build and run on a device with a TrueDepth camera (iPhone X or later).

### 2. Capture Sessions

1. Launch the app on device
2. Enter **subject name** and **lighting notes**
3. Tap **Track** to start ARKit face tracking
4. Tap **Record** to begin capturing frames
5. Follow the capture protocol below
6. Tap **Stop** then **Export** to save the session

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

# Download the face landmarker model
wget -O face_landmarker_v2.task \
  https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task

# Process a session
python process_captures.py path/to/session_20260521_143022/
```

This produces `mediapipe_blendshapes.csv` alongside `arkit_blendshapes.csv` in the session directory.

### 5. Run Accuracy Benchmark

After processing one or more sessions, run the benchmark to compare ARKit vs MediaPipe blendshape accuracy:

```bash
# Basic report (generates reports/accuracy_report.md)
python benchmark.py --captures captures/ --output reports/

# With scatter plots (generates reports/plots/*.png)
python benchmark.py --captures captures/ --output reports/ --plots
```

The benchmark:
- Loads all `session_*` directories from the captures folder
- Aligns ARKit and MediaPipe frames by nearest timestamp (16ms tolerance)
- Computes per-blendshape MAE, RMSE, Pearson correlation, max error, and range ratio
- Classifies blendshapes into categories: good (MAE < 0.03), acceptable (0.03-0.08), low-accuracy (0.08-0.15), near-zero (correlation < 0.5)
- Outputs a ranked Markdown report and optional scatter plots

## Capture Protocol

Follow this sequence for each session to produce useful calibration data:

### Phase 1: Neutral Rest (5 seconds)
- Look straight at the camera with a relaxed, neutral expression
- Establishes baseline blendshape values

### Phase 2: Individual Blendshape Activation (60 seconds)
- Exaggerate each of these expressions one at a time, holding for ~3 seconds:
  1. **Cheek puff** — inflate both cheeks
  2. **Nose sneer left/right** — wrinkle nose to each side
  3. **Brow down left/right** — furrow each brow independently
  4. **Brow inner up** — raise inner brows (worried expression)
  5. **Jaw open** — wide open mouth
  6. **Eye wide left/right** — open each eye wide independently
  7. **Mouth pucker** — pucker lips forward
  8. **Tongue out** — extend tongue

### Phase 3: Natural Expressions (30 seconds)
- Cycle through natural expressions:
  - Talking (count 1-10 out loud)
  - Smiling, laughing
  - Surprised, confused
  - Frowning, angry
  - Winking (each eye)

### Phase 4: Head Rotation (20 seconds)
- Slowly rotate head through full range:
  - Yaw: look left → center → right
  - Pitch: look up → center → down
  - Roll: tilt head left → center → right

### Phase 5: Lighting Variation (15 seconds)
- If possible, change lighting during capture:
  - Turn off overhead light
  - Move to window (side-lit)
  - Use phone flashlight from different angles
- Note the lighting condition in the session metadata

## Minimum Dataset Requirements

| Parameter | Target |
|---|---|
| Subjects | 3+ (varied skin tones, facial structure) |
| Sessions per subject | 5 (varied lighting, times of day) |
| Frames per session | ~1000 (at 30fps, ~33 seconds of capture) |
| Total paired frames | 15,000+ |

## Output Format

### arkit_blendshapes.csv
```csv
timestamp,browDownLeft,browDownRight,browInnerUp,...,tongueOut
1716300000.123456,0.045000,0.042000,0.001000,...,0.000000
```

### mediapipe_blendshapes.csv
```csv
frame_index,timestamp,_neutral,browDownLeft,browDownRight,...,tongueOut
0,1716300000.123456,0.012345,0.038000,0.035000,...,0.000000
```

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
    "device_name": "Rio's iPhone",
    "frame_count": 1024,
    "ios_version": "18.5",
    "notes": "Overhead fluorescent lighting, afternoon",
    "subject_name": "subject_01"
}
```
