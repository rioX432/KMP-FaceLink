# cbridge conformance fixture

`live_link_conformance.json` is the **shared golden fixture** for the KMP-FaceLink
raw-tracking seam. It is the single cross-repo contract asserted by:

- **KMP-FaceLink** — the Live Link blendshape lowering (`LiveLinkBlendShapes`) and the
  head-transform format/units contract exposed by `kmp-facelink-cbridge`.
- **avatar-core** — the consumer adapter golden test, which additionally verifies the
  degrees→radians / meters conversion into the `[pitch, yaw, roll (rad), x, y, z (m)]`
  frame it expects.

Because both sides pin to the same file, the seam cannot silently drift.

## Format

| Field | Meaning |
|---|---|
| `blendShapeChannelOrder` | The 52 ARKit Live Link Face channel names, in wire order (index = position). |
| `headTransformLayout` | Field order of the 6-float head transform. |
| `units` | Rotation = degrees, position = meters, right-handed. |
| `degToRadFactor` | `pi/180`, for the consumer-side conversion. |
| `cases[]` | Input → expected pairs (see below). |

Each case:

- `input.blendShapes` — map keyed by ARKit `BlendShape.arKitName`. **Absent keys are
  0-filled** (this is how `tongueOut` behaves on Android/MediaPipe).
- `input.head` — head pose in the bridge's native units (degrees + meters).
- `expected.blendShapesLiveLink` — the 52-float array the bridge must emit.
- `expected.head` — head pose as emitted by the bridge (degrees + meters).
- `expected.headRadians` — rotations after the consumer's `deg × pi/180` conversion
  (e.g. `yaw = 10°` → `0.17453293 rad`).

## Cases

| Case | Asserts |
|---|---|
| `identity` | Neutral → all-zero blendshapes and neutral head. |
| `jaw_open_full` | `jawOpen = 1.0` maps to Live Link channel 17 (name-keyed, not ordinal). |
| `mediapipe_no_tongue` | Absent `tongueOut` → channel 51 is 0-filled; present channels preserved. |
| `head_yaw_10deg` | Degrees contract: `10°` yaw stays `10°` at the bridge, `0.17453293 rad` at the consumer. |
| `head_translate_x_0p1m` | Meters contract: `+0.1 m` X translation passed through unchanged. |

The KMP-FaceLink Kotlin side of these assertions lives in
`kmp-facelink/src/commonTest/kotlin/io/github/kmpfacelink/LiveLinkBlendShapesTest.kt`.
