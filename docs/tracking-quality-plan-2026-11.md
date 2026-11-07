# Face-first tracking SDK: quality and execution plan

Approved: 2026-11-07. Audited source baseline:
`ec4443c491648a074a849c1dac267ffdc8027c2b`.

**Canonical execution/status index:**
[issue #972](https://github.com/rioX432/KMP-FaceLink/issues/972).
It links 40 leaf issues. Implement a leaf issue, not this entire plan in one PR.
The individual issues contain scope, prerequisites, tests, acceptance criteria
and non-goals. Read current source before implementation.

This document records a decision and work breakdown. It does not establish that
runtime fixes, module deletion, model training or physical qualification have
been completed. The planning PR changes documentation only.

## Product definition

A face-first avatar tracking SDK that makes its accuracy, timing, reliability,
interoperability and limits reproducible. Support face plus hands/upper body,
then full body, without sacrificing face quality to a whole-body fps headline.

Keep the measured estimator output separate from derived/corrected signals,
calibration, temporal interpolation and avatar presentation. A pleasant-looking
animation is not automatically a more accurate measurement.

### Retain

- Face, hand and body tracking; Android/iOS face quality is the first target.
- Minimal public interfaces; internal replaceable estimator implementations.
- Capability, missing-state, provenance and exact input/context contracts.
- Personal calibration and causal smoothing when supported by evidence.
- Live2D/VRM mapping, explicit rig capabilities and staged humanoid retargeting.
- VMC/IFM/VTS, minimal tracking transport, C bridge and useful native adapters.
- Recording/replay and internal authorized capture/evaluation tooling.
- Explicit experimental status for unqualified JVM/Web/backend combinations.

### Retire from the active SDK

`kmp-facelink-pipeline`, `kmp-facelink-llm`, `kmp-facelink-voice`,
`kmp-facelink-interact`, and the non-recording portions of
`kmp-facelink-actions`, including exclusive samples, dependencies, native setup,
distribution entries and unsupported current claims.

First extract recording/playback into a small tracking-only module. Do not
remove core gesture observations, hand/body tracking, static avatar vowel
mappings, capture authorization, or tracking transport because they were used
by a retired feature. Preserve published history and document intentional
retired-module API/coordinate changes.

## Architecture boundary

```text
Camera input + immutable frame identity/ownership
    -> platform estimator + optional qualified sidecar
    -> explicit candidate selection / calibration / causal temporal processing
    -> values + availability + capture time + provenance
    -> avatar retargeting and tracking-output adapters
```

Do not create a second parallel evidence/enrollment system. Reuse existing
candidate receipts, capability matching, calibration lifecycle and private
capture contracts. Renderer integration stays in host adapters/reference scenes,
not a new renderer product inside the core SDK.

## Implementation contract for AI agents

1. Read AGENTS.md, #972, the selected leaf issue and its blockers. Claim the
   issue in a comment with the base SHA. Use one branch/worktree and one focused
   PR per leaf. No direct master writes, automatic merges or releases.
2. Start bugs with a reproducible regression where feasible. Attach exact
   commands/results, API/behavior changes, migration notes and limitations.
   Missing Xcode, hardware, credentials or private data is a blocker, not a
   reason to weaken checks or report unrun tests as passing.
3. Keep every module-removal PR buildable. Change module sources, consumers,
   settings, API inventories, BOM/SPM/CI/publication references atomically for
   that removal. Root build files, SPM generation and workflow YAML are a
   serialized integration lane; avoid concurrent overwrites.
4. Preserve retained APIs and cbridge channel order/units. Only update intended
   API dumps. Discover actual Gradle tasks with `:<module>:tasks --all`; run
   relevant tests and `sdkQualityCheck` on the required host. Compilation of a
   device test is not execution of that test.
5. Do not open, capture, upload or train on biometric measurement data without
   the existing explicit authorization protocol. Public CI uses synthetic or
   permitted non-biometric fixtures. Preserve raw-data privacy and no-write
   preview boundaries.
6. Freeze protocol, candidate/configuration identities and thresholds before
   held-out scoring. Split by participant/session/device, never random frames
   from one recording. ARKit is a reference signal, not physical ground truth.
7. Keep missing, unsupported, rejected and not-ready distinct from valid zero.
   Do not infer confidence or qualification from an API label or successful
   model load. Keep corrections and unqualified delegate paths default-off.
8. Conditional experiments can end with retain-baseline/no-go. Verify current
   primary documentation, exact model/runtime availability and licenses at the
   time of implementation. Paper claims are not measured SDK results.

## Leaf issue map

Issue status and full prerequisites live in GitHub. This table is an index,
not an assertion that tasks have been completed.

| Code | Issue | Deliverable |
|---|---|---|
| S01 | [#973](https://github.com/rioX432/KMP-FaceLink/issues/973) | Retained/removal dependency and migration manifest |
| S02 | [#974](https://github.com/rioX432/KMP-FaceLink/issues/974) | Extract recording/playback |
| S03 | [#975](https://github.com/rioX432/KMP-FaceLink/issues/975) | Remove AI avatar pipeline |
| S04 | [#976](https://github.com/rioX432/KMP-FaceLink/issues/976) | Remove LLM integrations |
| S05 | [#977](https://github.com/rioX432/KMP-FaceLink/issues/977) | Remove ASR/TTS/Whisper-only dependencies |
| S06 | [#978](https://github.com/rioX432/KMP-FaceLink/issues/978) | Remove non-recording actions/effects/emotion orchestration |
| S07 | [#979](https://github.com/rioX432/KMP-FaceLink/issues/979) | Remove Twitch/YouTube interaction |
| S08 | [#980](https://github.com/rioX432/KMP-FaceLink/issues/980) | Enforce final module/distribution inventory |
| S09 | [#981](https://github.com/rioX432/KMP-FaceLink/issues/981) | Align current documentation and migration |
| R01 | [#982](https://github.com/rioX432/KMP-FaceLink/issues/982) | Identity-safe independent frame ownership |
| R02 | [#983](https://github.com/rioX432/KMP-FaceLink/issues/983) | Bounded shared per-consumer leases |
| R03 | [#984](https://github.com/rioX432/KMP-FaceLink/issues/984) | Idempotent lifecycle and callback rejection |
| R04 | [#985](https://github.com/rioX432/KMP-FaceLink/issues/985) | Latest-frame delivery and loss semantics |
| R05 | [#986](https://github.com/rioX432/KMP-FaceLink/issues/986) | Capture/submission/result clock provenance |
| R06 | [#987](https://github.com/rioX432/KMP-FaceLink/issues/987) | Partial failure without healthy-stream deadlock |
| R07 | [#988](https://github.com/rioX432/KMP-FaceLink/issues/988) | Bounded temporal holistic join |
| R08 | [#989](https://github.com/rioX432/KMP-FaceLink/issues/989) | iOS ARFrame/Vision shared-input prototype |
| Q01 | [#991](https://github.com/rioX432/KMP-FaceLink/issues/991) | Same-SHA CI gate before publication |
| Q02 | [#992](https://github.com/rioX432/KMP-FaceLink/issues/992) | Immutable tags and SPM assembly |
| Q03 | [#993](https://github.com/rioX432/KMP-FaceLink/issues/993) | Model/config/artifact provenance manifest |
| Q04 | [#994](https://github.com/rioX432/KMP-FaceLink/issues/994) | Packaged Maven/SPM consumer smoke tests |
| Q05 | [#995](https://github.com/rioX432/KMP-FaceLink/issues/995) | Correct coverage/skipped-test reporting |
| Q06 | [#996](https://github.com/rioX432/KMP-FaceLink/issues/996) | Schema/coordinate/missing-signal conformance |
| Q07 | [#997](https://github.com/rioX432/KMP-FaceLink/issues/997) | Authorized event-level replay evaluator |
| Q08 | [#998](https://github.com/rioX432/KMP-FaceLink/issues/998) | Sustained-load and end-to-end timing harness |
| Q09 | [#1001](https://github.com/rioX432/KMP-FaceLink/issues/1001) | Qualification report and claim gate |
| E01 | [#990](https://github.com/rioX432/KMP-FaceLink/issues/990) | Preregistered protocol and human-data boundary |
| E02 | [#999](https://github.com/rioX432/KMP-FaceLink/issues/999) | Lightweight personal calibration |
| E03 | [#1000](https://github.com/rioX432/KMP-FaceLink/issues/1000) | Authorized native/contour/personalized evaluation |
| E04 | [#1002](https://github.com/rioX432/KMP-FaceLink/issues/1002) | Conditional RGB/geometry ablations |
| E05 | [#1003](https://github.com/rioX432/KMP-FaceLink/issues/1003) | Conditional Android model/delegate qualification |
| E06 | [#1004](https://github.com/rioX432/KMP-FaceLink/issues/1004) | Causal smoothing comparison |
| E07 | [#1005](https://github.com/rioX432/KMP-FaceLink/issues/1005) | Holistic/Composite face-first comparison |
| E08 | [#1006](https://github.com/rioX432/KMP-FaceLink/issues/1006) | Evidence-gated puff detection adoption |
| A01 | [#1007](https://github.com/rioX432/KMP-FaceLink/issues/1007) | Rig capability contract |
| A02 | [#1008](https://github.com/rioX432/KMP-FaceLink/issues/1008) | Live2D reference-rig conformance |
| A03 | [#1009](https://github.com/rioX432/KMP-FaceLink/issues/1009) | VRM reference-rig conformance |
| A04 | [#1010](https://github.com/rioX432/KMP-FaceLink/issues/1010) | Upper-body retargeting baseline |
| A05 | [#1011](https://github.com/rioX432/KMP-FaceLink/issues/1011) | Conditional lower-body retargeting |
| A06 | [#1012](https://github.com/rioX432/KMP-FaceLink/issues/1012) | Optional foot-contact presentation stabilization |

## Execution order

Start with S01, R01, R04, Q01, Q05 and E01, subject to shared-file coordination.

- Extract recording before removing actions. Remove pipeline before its LLM/
  voice/action dependencies. Remove actions before interact. Keep each step
  independently buildable; enforce the final inventory afterward.
- Fix ownership and lifecycle before extending image inference. Fix time and
  availability contracts before temporal holistic fusion.
- Compare native MediaPipe, the existing frozen contour rule and a separate
  personalized candidate before proposing a new image model. The frozen rule
  remains a historical baseline; do not tune it on held-out evaluation.
- Keep puff detection distinct from continuous intensity. Presentation easing
  of a binary state is not a certified physical coefficient.
- Face-only quality precedes upper-body/full-body qualification. Official
  Holistic is a comparison candidate, not an assumed mandatory replacement.
- Publication uses the exact verified artifact. Tags are immutable; source,
  model, preprocessing, configuration, evaluator and binary identities bind
  qualification. Source-green but unmeasured targets remain unqualified.

## Quality evidence

Use the existing public source/synthetic, private replay and independent-device
boundaries from [Quality Gates](quality-gates.md). Add isolated packaged-consumer
checks, sustained camera+renderer testing and artifact-bound reports through
Q04/Q08/Q09. Report missing/skipped/failed/unknown states explicitly.

The initial 95% event recall, 0.1 false activations per negative hour, incremental
p95 5 ms and host end-to-end p95 100 ms figures discussed during planning are
**candidate targets**, not current results or universal acceptance guarantees.
A named-device/profile pilot establishes measurement procedure and costs before
thresholds are frozen. Report uncertainty, independent units and exposure;
frames from a recording are not independent participants. Software stage timing
does not alone establish optical glass-to-glass latency.

## Existing backlog

Keep #960 (JVM qualification) and #961 (JVM INT8) deferred, not falsely completed.
Keep #962 for retained-code compiler warnings and #963 for historical research
classification; S09 owns current guidance. Reconciliation comments are recorded
on all four. Do not reopen retired broad feature work while following this plan.
