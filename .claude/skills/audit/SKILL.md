---
name: audit
description: "Audit KMP-FaceLink for API quality, tech debt, thread safety, and platform parity — then create GitHub Issues"
argument-hint: "[scope: core|android|ios|sample|all (default: all)]"
user-invocable: true
disable-model-invocation: true
allowed-tools:
  - Bash(./gradlew detekt)
  - Bash(./gradlew ktlintCheck)
  - Bash(gh issue create:*)
  - Bash(gh issue list:*)
  - Bash(git log:*)
  - Glob
  - Grep
  - Read
  - Task
  - TaskCreate
  - TaskUpdate
  - TaskList
  - AskUserQuestion
  - mcp__codex__codex
  - mcp__codex__codex-reply
---

# /audit — KMP-FaceLink Codebase Health Audit

Audit KMP-FaceLink for API quality, tech debt, thread safety, and platform parity issues. Findings become GitHub Issues.

**Scope:** "$ARGUMENTS" (core | android | ios | sample | all — default: all)

---

## Step 1: Setup

Create task tracker:

1. "Run static analysis (detekt + ktlint)"
2. "Scan tech debt (TODOs, deprecated APIs, dead code)"
3. "Scan API quality (explicitApi, Flow patterns, public surface)"
4. "Scan platform parity (expect/actual symmetry)"
5. "Scan architecture (module deps, coroutines, resource leaks)"
6. "Aggregate findings"
7. "Create GitHub Issues"

Parse `$ARGUMENTS`:
- `core` → scan KMP modules only (kmp-facelink, kmp-facelink-*)
- `android` → scan androidApp/ only
- `ios` → scan iosApp/ only
- `sample` → scan both sample apps
- `all` or empty → run everything

---

## Step 2: Static Analysis (Parallel)

Mark task 1 `in_progress`. Run **in parallel** (single message).

### 2a. Detekt (skip if scope = android or ios)

```bash
./gradlew detekt 2>&1 | tail -80
```

Parse for:
- `LongMethod` violations (>60 lines)
- `TooManyFunctions` violations
- `MagicNumber` violations
- `ImportOrdering` violations
- `ForbiddenComment` (TODO/FIXME in production code)
- `MaxLineLength` violations (>150 chars)

### 2b. ktlint (skip if scope = android or ios)

```bash
./gradlew ktlintCheck 2>&1 | tail -40
```

Mark task 1 `completed`.

---

## Step 3: Parallel Code Scans

Mark tasks 2–5 `in_progress`. Launch **4 Task subagents in parallel** (`subagent_type: "Explore"`, `model: "haiku"`).

---

### Subagent A: Tech Debt Scanner

```
Scan for tech debt in the KMP-FaceLink library.

Root: /Users/rio/workspace/projects/KMP-FaceLink/

## What to find:

### 1. TODO / FIXME / HACK comments
Use Grep to find: `TODO|FIXME|HACK|XXX|WORKAROUND`
- In: kmp-facelink*/src/, androidApp/, iosApp/
- Exclude: build/, .gradle/
- For each: record file, line, comment text, surrounding context (±2 lines)

### 2. Deprecated API usage
- Kotlin: `@Deprecated` annotation usage in non-deprecated code
- Platform: deprecated Android/iOS APIs in platform implementations

### 3. Hardcoded values
- Magic numbers outside the detekt allowlist (-1, 0, 1, 2, 16, 1000)
- Hardcoded strings used as configuration values (URLs, timeouts, thresholds)

### 4. Dead code indicators
- Private functions that are never called (check with Grep for function name)
- `@Suppress("UNUSED_PARAMETER")` without justification
- Commented-out code blocks

Return format (JSON-like list):
[
  {
    "category": "TODO|DEPRECATED|HARDCODED|DEAD_CODE",
    "severity": "high|medium|low",
    "file": "relative/path/to/file.kt",
    "line": 42,
    "description": "What the issue is",
    "snippet": "the problematic code line(s)"
  }
]
```

---

### Subagent B: API Quality Scanner

```
Scan for API quality issues in KMP-FaceLink library modules.

Root: /Users/rio/workspace/projects/KMP-FaceLink/
Module paths: kmp-facelink/src/, kmp-facelink-avatar/src/, kmp-facelink-actions/src/,
              kmp-facelink-effects/src/, kmp-facelink-stream/src/, kmp-facelink-voice/src/,
              kmp-facelink-rive/src/, kmp-facelink-live2d/src/

## Checks to perform:

### 1. explicitApi compliance
- Public classes/functions/properties in commonMain without explicit `public` keyword
- Internal platform implementations that accidentally expose `public` symbols
- Any declaration in src/commonMain/ missing explicit visibility modifier

### 2. Experimental API annotation
- Public APIs using unstable features (Live2D renderer, experimental platform APIs)
- Do these have `@ExperimentalFaceLinkApi` or equivalent annotation?
- Callers using experimental APIs without `@OptIn`

### 3. Platform code leakage
- Platform-specific types imported in commonMain (android.*, platform.*)
- `expect` declarations without both androidMain and iosMain actuals

### 4. Public API minimalism
- Public classes exposing internal implementation details
- Mutable state exposed publicly (should be read-only `val` or `StateFlow`)
- Platform-specific types in public API signatures

### 5. Flow pattern compliance
- Callbacks used where Flow should be used in public API
- `MutableStateFlow` exposed directly (should expose `StateFlow`)
- `Channel` exposed directly (should expose `Flow`)

Return the same JSON format as Subagent A.
```

---

### Subagent C: Platform Parity Scanner

```
Scan for platform parity issues in KMP-FaceLink.

Root: /Users/rio/workspace/projects/KMP-FaceLink/

## Checks to perform:

### 1. expect/actual completeness
For each module, find all `expect` declarations in commonMain.
For each expect:
- Is there a matching `actual` in androidMain?
- Is there a matching `actual` in iosMain?
- Flag any expect without both actuals.

### 2. Feature parity
For each public API in commonMain:
- Does Android implementation support all features?
- Does iOS implementation support all features?
- Are platform-specific limitations documented?

### 3. Thread safety symmetry
- `PlatformLock` usage: if present in Android impl, check iOS impl has equivalent
- `AtomicInt` usage: consistent across platforms?
- Shared mutable state without synchronization (data race risk)

### 4. Error handling symmetry
- Exceptions thrown by Android impl but not iOS impl (or vice versa)
- Platform-specific errors not mapped to common types

Return the same JSON format as Subagent A.
```

---

### Subagent D: Architecture Scanner

```
Scan KMP-FaceLink architecture for structural issues.

Root: /Users/rio/workspace/projects/KMP-FaceLink/
Architecture rules:
- Core module (kmp-facelink): base tracking API + models
- Extension modules (kmp-facelink-*): depend on core only
- No circular dependencies between modules
- Platform implementations are `internal`
- Public API only in commonMain

## Checks to perform:

### 1. Module dependency violations
- Extension modules importing from other extension modules
- Core module importing from extension modules (circular)
- Check each module's build.gradle.kts for dependency declarations

### 2. Coroutines/Flow misuse
- `GlobalScope` usage (should use structured concurrency)
- `runBlocking` in library code (blocks calling thread)
- Flow builders that launch coroutines without proper cancellation

### 3. Resource leak indicators
- `start()` methods without corresponding `stop()` method
- Resources (camera, mic, network) not released on `stop()`
- Missing `release()` or `close()` on Closeable resources

### 4. Missing error handling
- `suspend fun` that can throw but returns non-Result type without documentation
- Empty catch blocks swallowing exceptions
- Native platform callbacks that could crash if they throw

Return the same JSON format as Subagent A.
```

---

## Step 4: Aggregate Findings

Mark tasks 2–5 `completed`. Mark task 6 `in_progress`.

Collect all results and:

### Deduplication
If same file+line appears in static analysis and code scan, merge into one finding.

### Severity Classification

| Severity | Criteria |
|----------|----------|
| **Critical** | Crash risk, data race, memory leak |
| **High** | API contract violation, missing actual, feature parity gap |
| **Medium** | explicitApi violation, hardcoded threshold, missing error handling |
| **Low** | TODO comment, dead code, minor code smell |

### Group by Category
1. **Bugs** (Critical + High functional issues)
2. **API Quality** (explicitApi, Flow patterns, public API surface)
3. **Platform Parity** (expect/actual, feature gaps)
4. **Tech Debt** (TODOs, deprecated, dead code)
5. **Architecture** (module deps, coroutines, resource leaks)

Mark task 6 `completed`.

---

## Step 5: Present Findings

```
## Audit Report — KMP-FaceLink

Scope: {all | core | android | ios | sample}
Found: {N} issues across {K} files

### Bugs (N)
| # | Severity | File | Line | Description |
|---|----------|------|------|-------------|
...

### API Quality (N)
...

### Platform Parity (N)
...

### Tech Debt (N)
...

### Architecture (N)
...
```

---

## ── AskUserQuestion: Issue Creation ──

**Q1: Which findings should become GitHub Issues?**
- All Critical + High findings (recommended)
- All findings
- Let me select by category
- None (report only)

**Q2: Labels to apply?**
- Auto-detect per finding (recommended)
- No labels

If more than 5 issues will be created, show full list and ask to confirm.

---

## Step 6: Create GitHub Issues

Mark task 7 `in_progress`.

For each selected finding:

```bash
gh issue create \
  --title "{Category}: {concise description} ({filename}:{line})" \
  --body "$(cat <<'EOF'
## Summary
{Description}

## Location
`{relative/file/path}:{line}`

## Details
{snippet}

## Impact
{Why this matters}

## Suggested Fix
{Concrete suggestion}
EOF
)" \
  --label "{auto-detected labels}"
```

### Label Mapping

| Category | Labels |
|----------|--------|
| Bugs (Critical) | `bug`, `priority: high` |
| Bugs (High) | `bug` |
| API Quality | `api` |
| Platform Parity | `platform-parity` |
| Tech Debt | `tech-debt` |
| Architecture | `tech-debt` |

If a label doesn't exist, skip it silently.

Mark task 7 `completed`.

---

## Step 7: Final Report

```
## Audit Complete

Scope: {scope}
Issues found: {total}
GitHub Issues created: {N}

| Category | Found | Created |
|----------|-------|---------|
| Bugs | N | N |
| API Quality | N | N |
| Platform Parity | N | N |
| Tech Debt | N | N |
| Architecture | N | N |

Static Analysis:
- Detekt: {pass / N violations}
- ktlint: {pass / N violations}
```

---

## Error Handling

| Situation | Action |
|-----------|--------|
| detekt not available | Skip, note in report |
| Subagent returns no findings | Note "No issues found in this category" |
| Label doesn't exist | Skip silently |
| `gh issue create` fails | Report error, user can create manually |
| 0 findings total | Report "Codebase looks healthy!" and stop |
