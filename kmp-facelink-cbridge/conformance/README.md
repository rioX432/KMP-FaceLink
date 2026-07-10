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
| `full_face` | All 52 channels non-zero with distinct values (`(index+1)/256`, exact in f32) — proves the total channel order, not just single-channel placement. |
| `head_pitch_neg5deg` | Negative-sign pitch: `-5°` stays `-5°` at the bridge, `-0.08726646 rad` at the consumer. |
| `head_roll_neg5deg` | Negative-sign roll: `-5°` stays `-5°` at the bridge, `-0.08726646 rad` at the consumer. |
| `head_translate_xyz` | Simultaneous X/Y/Z translation (`+0.05 / -0.1 / +0.2 m`, including a negative component) passed through unchanged. |

The KMP-FaceLink Kotlin side of these assertions lives in two places:

- `kmp-facelink/src/jvmTest/kotlin/io/github/kmpfacelink/LiveLinkConformanceFixtureTest.kt` —
  **parses this fixture** and asserts every case against `LiveLinkBlendShapes.lower(...)`,
  the channel order, and the head passthrough / degrees→radians consistency. Runs in CI
  via `:kmp-facelink:allTests`.
- `kmp-facelink/src/commonTest/kotlin/io/github/kmpfacelink/LiveLinkBlendShapesTest.kt` —
  target-portable literal smoke tests (no file I/O, run on every KMP target).

When this fixture changes, avatar-core must re-mirror it into
`crates/avatar-protocol/conformance/` (and update the SHA-256 in its conformance
README) — its sibling drift-check test fails until re-mirrored.
