# Quality Gates

KMP-FaceLink separates deterministic source checks from measurement-based tracking quality. A
green source build is necessary, but it cannot establish expression accuracy, device performance,
capture usability, or production readiness.

## Tier 1 — Pull-request source contract

Run:

```bash
./gradlew sdkQualityCheck
```

This gate covers public API dumps, static analysis, core/capture tests, Android and iOS compilation,
Android debug/test packaging, an unsigned iOS Simulator build, module/document dependency
agreement, authorization logic, synthetic solver wiring, tracked-path privacy checks, and the
pinned OpenSeeFace FP32 supply/runtime contract. The JVM synthetic zero-input contract tests load
both real public model files, verify their SHA-256 and ONNX metadata, and execute zero tensors through detection and landmark
decoders. This proves model/decoder mechanics, not tracking quality or sustained performance.

The synthetic cheek-puff fixture has one intended signal and two negative controls. It catches
decision-wiring regressions only; it is not a face dataset and yields no accuracy, sensitivity, or
false-positive claim.

## Tier 2 — Authorized same-recording replay

The private replay task is deliberately excluded from ordinary CI:

```bash
CHEEKPUFF_TESTDATA=/authorized/local/session \
CHEEKPUFF_FROZEN_RULE=/authorized/local/frozen_rule.json \
  ./gradlew :kmp-facelink:privateCheekPuffQualityCheck --rerun-tasks
```

It must run only on an authorized local or isolated runner. Raw frames, landmarks, transforms, and
per-frame coefficients must not enter Git or hosted CI logs. Passing this task establishes parity
with the frozen implementation on the same recording; it does not establish quality on an
independent session, participant, device, or live product path.

## Tier 3 — Independent and physical qualification

Before product adoption, use data that was not used to design the solver or choose its threshold.
The protocol must preregister the exact source/binary/model/runtime context, take-level labels,
positive and named-negative scenarios, missing/readiness accounting, and product acceptance costs.
Frames are repeated observations within a take, not independent samples. ARKit may be a reference
signal but is not physical ground truth.

Physical qualification is platform-specific and must cover exact-binary launch, camera permission,
preview/mesh visibility, callback continuity and tail behavior, lifecycle, no-write preview mode,
sustained load/thermal behavior, and guided-capture accessibility. Android and iOS results are not
pooled into a cross-platform accuracy or speed ranking.

The current internal apps are:

- `captureApp`: Android guided capture and explicit developer preview-check route.
- `captureIosApp`: iOS guided capture, `--preview-check`, and separately signed
  `--performance-diagnostic` route.
- `kmp-facelink-capture`: shared workflow, manifest, gate, evaluator, and storage contract.

Normal guided takes save private measurement payloads. Preview-check source paths do not call the
recording API, but physical no-write behavior remains a device qualification until verified on the
exact binary. Performance diagnostics require an exact-binary signed run authorization and write
aggregate-only output by design.

## Current evidence boundary

Existing recordings are useful for regression and implementation-parity checks. They cannot fully
qualify a solver tuned on the same material. Independent participant/session data and a defined
product acceptance threshold remain required before enabling geometric correction by default.
