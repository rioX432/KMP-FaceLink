# FaceLinkCapture iOS coefficient study

The normal iOS app starts the immutable
`IOS_REGISTERED_COEFFICIENT_17_V1` protocol. It records ARKit coefficients,
transform, callback sequence/time, and manifests under
`COEFFICIENTS_ONLY_V1`; it does not record camera pixels or common 478-point
landmarks.

This mode is deliberately different from Android's broad 22-take guided study
and Android-only learned-patch Session C. Do not pool their run identities or
continue one platform's progress on the other.

## Before recording

1. Use only an exact clean reviewed build and its authorized device install.
2. After the first neutral take, the app must confirm that at least 1 GiB remains
   available on the capture volume. A failure blocks slot two.
3. Confirm camera permission, portrait orientation, visible native camera/mesh
   preview, face guide, power, and thermal comfort.
4. The participant/device owner must explicitly authorize this run. No camera
   or recording action follows from a source build alone.

The camera preview is the primary recording surface, with the guide drawn over
it. During recording it receives the flexible vertical space instead of the old
fixed 110 dp slot. This is a source/layout contract, not evidence that the
preview is readable or correctly aligned on a physical device.

The ephemeral diagnostic strip contains only mode (`確認のみ` or `収録`), input
category, result age in seconds (capped at 99), face category, and the five gate
bits `F C S B D` (face, centered, sized, bright, distanced). It is not logged,
saved, or exported and contains no coordinates, transforms, coefficients, or
pixels. `入力あり` means a result callback was observed; it does not mean ARKit
tracked a face. `確認のみ` is preview-check mode and never starts recording.

An ARSession failure or interruption is a terminal frame-source loss. The Swift
runtime reports the stable `frame_source` category before it stops, clears the
shared gate through KMP, and does not restore retry when an older retryable take
outcome arrives later. Terminal UI takes priority over preflight and open sheets;
reopen the app rather than continuing the same run.

The opening screen must show 17 total takes. If it shows 22, 12, or any other
count, stop before recording.

## Recording

Follow the single instruction shown in the guide. Each take uses prepare 2 s,
neutral 4 s, hold 4 s, release 4 s, hold 4 s, and release 4 s. The two holds are
part of one take. The fixed order is:

1. neutral
2. jaw open
3. cheek puff light
4. cheek puff mid
5. cheek puff
6. left cheek only
7. right cheek only
8. blow, held
9. blow, natural
10. pucker
11. smile
12. tongue out
13. nose sneer
14. speech
15. cheek puff light, second take
16. cheek puff mid, second take
17. cheek puff, second take

After the first neutral take, the app stops progress until the schema-5
coefficient-only preflight confirms at least 1 GiB remains, `framesStored = 0`,
no pixel artifact, stream lengths/hashes, counts, and sequence/timestamp
ordering. This is a storage-integrity check, not an expression-quality score.

Use the in-app stop/rest control whenever needed. Do not delete or repeat a take
because its coefficients look unfavorable. A repeat is allowed only for a
recorded operational or app-declared integrity failure.

## Failure recovery

- Only a take whose streams closed, manifest synced, and directory published is
  visible under `takes/` or counted as progress.
- A geometry, sink-open, sink-close, manifest, or publish failure removes the
  incomplete directory. If cleanup itself fails, session/take setup fails
  closed instead of silently continuing.
- If the app or OS stops mid-take, the next session reclaims only direct
  children of the capture-owned `tmp/` root. It never scans or deletes
  `takes/`, the active run identity, or anything outside the capture root.
- A process-local exclusive root lease rejects a second live session for the
  same capture root, so stale recovery cannot delete the first session's
  in-flight directory. Process termination releases the lease implicitly.
- A frame-source failure clears all five live gate flags and terminates the run;
  it does not return to take-level retry. Reopen the capture screen, and stop if
  it recurs.
- The Swift runtime becomes callback-eligible under its lifecycle lock before
  `ARSession.run`. Setup/run failure and ARSession failure/interruption all use
  the same stable frame-source report-before-stop path.
- If retake deletion is already in flight when the run becomes terminal, its
  disk operation may finish, but the delayed continuation cannot change saved
  or selected UI state, prepare another take, or replace the terminal message.
- This source/test contract does not prove power-loss durability or physical
  APFS allocation. Observe those separately in the authorized device pilot.

## Storage and privacy

- The iOS canonical-RGBA profile is unavailable and fails closed; do not use a
  legacy raw-frame build as a substitute.
- Private coefficients and transforms remain in the app container or approved
  local private-data root. Do not send them to Git, web services, review packets,
  PDFs, or slides.
- A source/protocol/profile/binary change ends the run. Do not append later
  takes under the old run identity.
