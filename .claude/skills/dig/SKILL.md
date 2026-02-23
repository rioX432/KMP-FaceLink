---
description: "Clarify ambiguities in plans with structured questions"
---

# Dig — Structured Ambiguity Resolution for KMP

Resolve ambiguities before implementation by generating structured questions with options. Auto-decides choices that follow established KMP-FaceLink patterns.

## When to Use

- After investigation is complete but before decomposition
- When there are design choices, trade-offs, or unclear requirements
- When the approach could go multiple valid directions

## Process

### Step 1: Extract Ambiguities

From the investigation results, identify every decision point:

| # | Ambiguity | Category | Auto-decidable? |
|---|-----------|----------|-----------------|
| 1 | ... | source-set / module / api-design / expect-actual / flow / threading / performance | yes/no |

### Step 2: Apply Auto-Decide Rules

For each auto-decidable ambiguity, apply the matching rule below. Record the decision with the rule name.

#### Source Set Placement
- Uses platform API (MediaPipe, CameraX, ARKit, Vision, AVFoundation)? → `androidMain` or `iosMain`
- Pure Kotlin logic, data classes, interfaces, utilities? → `commonMain`
- Needs different implementations per platform but same API? → `expect` in commonMain + `actual` in platform source sets

#### expect/actual Pattern
- Platform-specific class with methods? → Define `interface` in commonMain + `expect fun create*(): Interface`
- Platform type alias only? → `expect class` (e.g., PlatformContext)
- Platform constant/config? → `expect val` in commonMain
- Business logic? → NEVER use expect/actual, keep in commonMain

#### Module Placement (from AGENTS.md dependency tree)
- Core tracking data types, trackers? → `kmp-facelink`
- BlendShape → Live2D parameter mapping? → `kmp-facelink-avatar`
- Gesture/expression triggers? → `kmp-facelink-actions`
- Visual face effects? → `kmp-facelink-effects`
- Live2D Cubism rendering? → `kmp-facelink-live2d`
- WebSocket/network streaming? → `kmp-facelink-stream`
- Audio (ASR/TTS/lip sync)? → `kmp-facelink-voice`
- Cross-cutting utility? → `kmp-facelink` core util/

#### Flow Usage
- Continuous data stream (tracking, audio)? → `Flow<T>`
- Observable state (tracking state, connection status)? → `StateFlow<T>`
- One-shot result? → `suspend fun`
- Never callbacks/listeners for streaming data

#### Thread Safety
- Mutable state shared across coroutines? → `PlatformLock` wrapper
- Counter/flag shared across threads? → `AtomicInt`
- Start/stop lifecycle methods? → Guard with state check under lock

#### API Visibility
- Public API types? → `commonMain` only, explicit `public` modifier
- Platform implementations? → `internal` visibility
- Extensions for inter-module integration? → `public` in the depending module

#### Naming
- BlendShape parameters? → ARKit names (e.g., `eyeBlinkLeft`, `jawOpen`)
- Tracking data classes? → `{Type}TrackingData` (e.g., `FaceTrackingData`)
- Factory functions? → `{Type}Factory.create(config, context)`
- Tracker interfaces? → `{Type}Tracker` with `start()`/`stop()`, `trackingData: Flow<T>`

### Step 3: Investigate Remaining Unknowns

For non-auto-decidable ambiguities:
1. Use `module-explorer` agent to find existing patterns
2. Use `platform-checker` agent to check if similar feature exists on one platform
3. Use `kmp-investigator` agent to trace related code paths

### Step 4: Ask User (max 3 rounds, max 4 questions per round)

Present remaining unknowns with `AskUserQuestion`:
- Context from investigation
- 2-4 concrete options with trade-offs
- Recommend the option matching existing codebase patterns

### Step 5: Output Decision Matrix

```markdown
## Dig Results: {requirement}

### Auto-Decided
| # | Decision | Rule | Result |
|---|----------|------|--------|
| 1 | Source set for X | platform API → iosMain | iosMain |

### Investigated
| # | Decision | Finding | Result |
|---|----------|---------|--------|
| 2 | ... | existing pattern in {file} | ... |

### User-Decided
| # | Decision | Choice | Reason |
|---|----------|--------|--------|
| 3 | ... | Option A | user preference |

### Assumptions (if any)
| # | Assumption | Risk |
|---|-----------|------|
```
