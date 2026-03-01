---
name: dev-all
description: "Process multiple GitHub Issues on a single branch. Investigates in parallel, implements sequentially, creates one PR."
argument-hint: "[issue numbers, e.g. #42 #43 #44, or empty for all open issues]"
user-invocable: true
disable-model-invocation: true
allowed-tools:
  - Bash(git checkout:*)
  - Bash(git add:*)
  - Bash(git commit:*)
  - Bash(git push:*)
  - Bash(git diff:*)
  - Bash(git log:*)
  - Bash(git status)
  - Bash(git branch:*)
  - Bash(git pull:*)
  - Bash(./gradlew *)
  - Bash(gh pr create:*)
  - Bash(gh issue view:*)
  - Bash(gh issue list:*)
  - Glob
  - Grep
  - Read
  - Edit
  - Write
  - Task
  - TaskCreate
  - TaskUpdate
  - TaskList
  - TaskGet
  - ToolSearch
  - AskUserQuestion
  - mcp__codex__codex
  - mcp__codex__codex-reply
---

# /dev-all — Batch Development on Single Branch

Process multiple GitHub Issues on **one branch** with **one PR**. Investigates in parallel, implements sequentially.

**Arguments:** "$ARGUMENTS"

## Why Single Branch?

Parallel branches can cause conflicts in shared files:
- **`iosApp/project.yml`**: iOS sample changes modify the same XcodeGen config
- **commonMain API files**: Multiple issues may extend the same public interfaces
- **Module build files**: Dependency additions in the same module conflict

Single branch with sequential implementation avoids all of these.

---

## Step 1: Resolve Target Issues

**If `$ARGUMENTS` is provided:** Extract issue numbers (e.g. `#42 #43 #44` or `42 43 44`).

**If `$ARGUMENTS` is empty:** Fetch all open issues:
```bash
gh issue list --state open --json number,title,labels,body --limit 100
```

---

## Step 2: Context Gathering (Parallel)

### 2a. Read Project Context

Read directly (not via subagent):
- `AGENTS.md` — architecture, modules, conventions
- `CLAUDE.md` — gotchas, workflow rules

### 2b. Parallel Issue Investigation

Launch **parallel Task subagents** (`subagent_type: "Explore"`, `model: "haiku"`) — one per issue.

Each subagent:
1. `gh issue view {NUMBER} --json title,body,labels,comments`
2. Use Grep/Glob to find related KMP code
3. Identify files that need changes and which modules are involved
4. Estimate scope: commonMain only, platform-specific, or both
5. Check for dependencies on other issues mentioned in the body
6. Return: issue summary, affected files, module scope, dependencies

### 2c. Create Task Tracker

Use `TaskCreate` for each issue:
- Subject: `#{number}: {title}`
- Description: scope, affected files, module impact, dependencies

---

## Step 3: Dependency Analysis & Execution Order

### 3a. Detect Dependencies

Check issue bodies for:
- `blocked by #NNN`, `depends on #NNN`, `after #NNN`, `waiting for #NNN`
- Labels: `blocked`, `blocked-by`, `on-hold`

For each blocker, check if open: `gh issue view NNN --json state`

### 3b. Execution Order

Topological sort:
1. Independent issues first (ascending by number)
2. Dependent issues after their dependencies
3. Circular dependencies → skip, report to user

### 3c. File Conflict Detection

Key conflict-prone files in KMP-FaceLink:
- `iosApp/project.yml` — iOS sample changes
- `kmp-facelink/src/commonMain/kotlin/.../model/*.kt` — core data types
- `kmp-facelink/src/commonMain/kotlin/.../FaceTracker.kt` — core interfaces
- `kmp-facelink/src/commonMain/kotlin/.../HandTracker.kt` — core interfaces

Order conflicting issues with simpler changes first.

---

## ── AskUserQuestion: Execution Plan ──

Present:
1. Ordered list of issues to implement
2. Dependencies detected
3. Skipped issues (circular deps, external blockers)
4. Estimated scope per issue

Ask user to confirm before proceeding.

---

## Step 4: Branch & Implement

### 4a. Create Branch

```bash
git checkout master && git pull
git checkout -b feature/batch-{first-issue}-{last-issue}
```

### 4b. Sequential Implementation

For each issue in execution order:

1. **Mark task `in_progress`**

2. **Implement via subagent** (`subagent_type: "general-purpose"`, `model: "sonnet"`):

```
Implement GitHub Issue #{NUMBER}: "{TITLE}" on the CURRENT branch.
Do NOT create a new branch — you are already on the correct feature branch.

## Issue Details
{issue body}

## Files Likely Affected
{from investigation}

## Previously Implemented Issues in This Batch
{list of completed issues: commit message, files changed}
→ BUILD ON those changes if files overlap.

## Project Conventions (from AGENTS.md + CLAUDE.md)
- explicitApi() enforced — all public symbols need explicit visibility
- Detekt LongMethod limit: 60 lines
- ImportOrdering: kotlinx.* belongs to * group, before java.*/javax.*
- Flow<T> for all streaming data — no callbacks in public API
- PlatformLock + AtomicInt for thread safety
- Platform implementations are internal

## Your Workflow
1. Read ALL affected files before making changes
2. Check if previous batch issues already modified these files
3. Follow existing patterns exactly
4. Commit: git add {specific files} (NO git add .)
         git commit -m "{concise message} (#{NUMBER})"
         No Co-Authored-By, no AI stamps

Report back: files changed, commit hash, any issues encountered.
```

3. **After subagent completes:**
   - Verify commit: `git log --oneline -1`
   - Mark task `completed`
   - Record changes for next subagent's context

4. **If subagent fails:**
   - Note the failure
   - Ask user: skip and continue, or stop?

---

## Step 5: Quality Gate

After ALL issues are implemented:

```bash
./gradlew build           # Build all modules
./gradlew allTests        # All tests across all modules
./gradlew detekt          # Static analysis (run twice if import reordering occurs)
./gradlew ktlintCheck     # Formatting check
```

### Failure Handling
- Fix and retry (max 3 attempts per check)
- Lint/test fixes go in a separate commit: `Fix lint/test issues`
- If still failing after 3 attempts → report to user, stop

---

## Step 6: PR Creation

```bash
git push -u origin feature/batch-{first-issue}-{last-issue}

gh pr create --title "Batch: #{first}–#{last} {brief summary}" --body "$(cat <<'EOF'
## Description

Batch implementation of the following issues:

- **#{NUMBER}**: {title} — {one-line summary}
...

## Related Issues

Closes #{NUMBER}
...

## Test Plan

- [x] All tests pass (`./gradlew allTests`)
- [x] Detekt pass (`./gradlew detekt`)
- [x] ktlint pass (`./gradlew ktlintCheck`)

## Review Checklist

- [x] Each issue implemented as a separate commit
- [x] explicitApi() compliance on all public symbols
- [x] Platform parity (androidMain + iosMain match)
- [x] Flow<T> used for all streaming data
- [x] No unnecessary dependencies added

## Breaking Changes

None
EOF
)"
```

---

## Step 7: Final Report

```
## Batch Development Summary

Branch: feature/batch-{first}-{last}
PR: {URL}

| # | Issue | Commit | Status |
|---|-------|--------|--------|
| 1 | #{42} Title | abc1234 | Done |
| 2 | #{43} Title | def5678 | Done |
| 3 | #{44} Title | — | Skipped (reason) |

Quality Gate: Build ✓ | Tests ✓ | Detekt ✓ | ktlint ✓
```

Mark all tasks `completed`.

---

## Error Handling

| Situation | Action |
|-----------|--------|
| Issue not found | Skip, warn in report |
| Circular dependency | Skip affected issues, report |
| Subagent implementation fails | Ask user: skip or stop |
| Tests fail after 3 attempts | Report to user, stop |
| Lint fails after 3 attempts | Report to user, stop |
| All issues blocked | Report, stop |
