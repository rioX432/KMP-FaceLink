# KMP-FaceLink scope: tracking quality, not a general VTuber platform

The previous broad extension strategy is superseded by the owner-approved
2026-11-07 direction: a face-first avatar tracking SDK with explicit, reproducible
quality boundaries.

Read [Tracking SDK quality plan](tracking-quality-plan-2026-11.md) and the
[execution index, issue #972](https://github.com/rioX432/KMP-FaceLink/issues/972).
The index links 40 focused implementation tasks, their ordering, conditional
experiments and the AI execution contract.

## Scope boundary

Keep face/hand/body acquisition and tracking, calibration, causal smoothing,
capability/provenance, avatar retargeting, tracking-data transport, recording and
quality tooling. Prioritize Android/iOS face quality before upper-body/full-body
expansion. Do not add LLM chat, ASR/TTS, stream-chat ingestion, generic emotion
classification or effect orchestration to this SDK.

Module removal is planned in issues #973 through #981. The planning PR does not
remove runtime modules or change public APIs. Extract recording before deleting
actions, preserve capture authorization and retained protocol/bridge contracts,
and keep every removal PR buildable.

The superseded strategy remains available in
[Git history at the audited baseline](https://github.com/rioX432/KMP-FaceLink/blob/ec4443c491648a074a849c1dac267ffdc8027c2b/docs/extension-strategy.md).
Its market, novelty and production-readiness statements are historical, not
current SDK guarantees or authorization to resume scope expansion.
