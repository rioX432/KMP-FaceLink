---
name: dev
description: "KMP E2E: investigate → dig → decompose → implement → test → review → PR"
argument-hint: "[GitHub issue number, e.g. 42]"
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
  - Bash(./gradlew *)
  - Bash(gh pr create:*)
  - Bash(gh issue view:*)
  - Glob
  - Grep
  - Read
  - Edit
  - Write
  - Skill
  - Task
  - TaskCreate
  - TaskUpdate
  - TaskList
  - TaskGet
  - ToolSearch
  - AskUserQuestion
  - mcp__codex__codex
  - mcp__codex__codex-reply
  - mcp__gemini-cli__ask-gemini
---

# /dev — KMP-FaceLink E2E Development Workflow

Resolve GitHub Issue #$ARGUMENTS from investigation to PR creation.

**Target:** Issue #$ARGUMENTS

## Setup: Create Task Tracker

Use `TaskCreate` to create a task for each phase at the start. This provides progress visibility and persistence across `/compact` and context compression.

Create these tasks immediately:
1. "Understand issue #$ARGUMENTS"
2. "Investigate codebase (kmp-investigator)"
3. "Resolve ambiguities (/dig)"
4. "Decompose into subtasks (/decompose)"
5. "Implement changes"
6. "Build verification"
7. "Review (kmp-reviewer + platform-checker + Codex)"
8. "Commit & create PR"

Use `TaskUpdate` to mark each task `in_progress` when starting and `completed` when done.

## Workflow Overview

```
Phase 1: Issue Understanding (GitHub Issue)
    ↓
Phase 2: Investigation (← kmp-investigator subagent)
    ↓
Phase 3: Ambiguity Resolution (/dig skill)
    ↓
Phase 4: Task Decomposition (/decompose skill)
    ↓
── AskUserQuestion: confirm approach + task list ──
    ↓
Phase 5: Branch Creation
    ↓
Phase 6: Autonomous Implementation Loop
    ↓
Phase 7: Build Verification (← build-runner subagent)
    ↓
Phase 8: Review (kmp-reviewer + platform-checker + Codex)
    ↓
── AskUserQuestion: commit + PR confirmation ──
    ↓
Phase 9: Commit & PR Creation
```

---

## Phase 1: Issue Understanding

Mark task 1 `in_progress`.

1. `gh issue view $ARGUMENTS --json title,body,labels,assignees,comments`
2. Extract: title, description, acceptance criteria, labels
3. Determine branch name:
   - Bug label → `fix/issue-$ARGUMENTS-{kebab-case-short-desc}`
   - Otherwise → `feat/issue-$ARGUMENTS-{kebab-case-short-desc}`

Mark task 1 `completed`.

---

## Phase 2: Investigation (Subagent)

Mark task 2 `in_progress`.

Delegate to `kmp-investigator` agent via `Task` tool:

```
Task(
  subagent_type: "general-purpose",
  model: "sonnet",
  prompt: <include issue details and instruct to follow .claude/agents/kmp-investigator.md>
)
```

The investigator traces:
1. commonMain API → expect/actual → platform implementations
2. Module dependency paths
3. Platform asymmetries
4. Existing tests and missing coverage

Only the structured report enters our context.

### Think Twice

After receiving the investigation report, re-evaluate:
1. Did the investigator actually read the code confirming the root cause?
2. Are there other possible causes not considered?
3. Is the impact analysis complete (all modules affected)?
4. Does this affect both platforms or just one?

If anything is ambiguous, use `AskUserQuestion` before proceeding. **Never assume.**

Mark task 2 `completed`.

---

## Phase 3: Ambiguity Resolution (/dig)

Mark task 3 `in_progress`.

Use the `/dig` skill with investigation results:

1. Extract decision points from the investigation
2. Auto-decide using KMP rules (source set, module, expect/actual, Flow, threading)
3. Investigate remaining unknowns with module-explorer or platform-checker
4. Ask user for true unknowns (max 3 rounds, max 4 questions per round)
5. Output Decision Matrix

Mark task 3 `completed`.

---

## Phase 4: Task Decomposition (/decompose)

Mark task 4 `in_progress`.

Use the `/decompose` skill:

1. Break task into KMP layer-ordered subtasks
2. Each subtask: What / Where / How / Why / Verify
3. Register all with `TaskCreate` and set dependencies
4. KMP order: commonMain API → expect → androidMain → iosMain → tests → sample → cross-cutting

Mark task 4 `completed`.

---

## ── AskUserQuestion: Approach Confirmation ──

Present to the user:
1. **Decision Matrix** (from dig)
2. **Task List** (from decompose, with dependencies)
3. **Investigation summary** (key findings)

Ask the user to confirm before implementation.

---

## Phase 5: Branch Creation

```bash
git checkout -b {branch-name}
```

Use the branch name from Phase 1.

---

## Phase 6: Autonomous Implementation Loop

Mark task 5 `in_progress`.

```
LOOP for each task (in dependency order):
  1. TaskUpdate → in_progress
  2. Read target code (MUST read before editing)
  3. Implement changes (Edit/Write)
  4. Self-verify (run Verify step from task description)
  5. TaskUpdate → completed
  6. Proceed to next task

INTERRUPT conditions:
  - Unexpected problem → AskUserQuestion for guidance
  - 3 consecutive failures → STOP and report to user
```

**Guidelines:**
- Follow existing code patterns (read surrounding code first)
- Follow AGENTS.md conventions (explicitApi, Detekt rules, Flow patterns)
- Keep changes minimal and focused

Mark task 5 `completed`.

---

## Phase 7: Build Verification (Subagent)

Mark task 6 `in_progress`.

Delegate to `build-runner` agent:

```
Task(
  subagent_type: "general-purpose",
  model: "haiku",
  prompt: <instruct to follow .claude/agents/build-runner.md, run ./gradlew build && ./gradlew allTests && ./gradlew detekt>
)
```

Only concise PASS/FAIL report returns. Full logs stay in subagent context.

### Failure Handling
1. Analyze the failure from the report
2. Fix the issue
3. Re-run via build-runner subagent
4. **Maximum 3 fix attempts** — if still failing, report to user and stop

Mark task 6 `completed`.

---

## Phase 8: Review

Mark task 7 `in_progress`.

### 8a. KMP Review (kmp-reviewer agent)
```
Task(
  subagent_type: "kmp-reviewer",
  prompt: <review changed .kt files>
)
```

### 8b. Platform Parity Check (platform-checker agent)
```
Task(
  subagent_type: "platform-checker",
  prompt: <check expect/actual completeness and feature parity on changed files>
)
```

Launch 8a and 8b **in parallel** (single message).

### 8c. Cross-Review (Codex)
Use `mcp__codex__codex` with `sandbox: read-only`:
- Verify architecture correctness
- Check for blind spots missed by kmp-reviewer

### Review Result Handling
- **Critical**: STOP. Report to user. Do NOT proceed to PR.
- **Warning**: Fix, re-run build verification (Phase 7)
- **Suggestion**: Note but don't block

Mark task 7 `completed`.

---

## ── AskUserQuestion: Commit + PR Confirmation ──

Show the user:
1. Summary of all changes
2. Build verification results
3. Review findings and resolutions
4. Proposed commit message (single line, no AI stamps)

---

## Phase 9: Commit & PR Creation

Mark task 8 `in_progress`.

### 9a. Commit
```bash
git add {specific files}
git commit -m "{concise message}"
```
- Explicit file staging (no `git add .`)
- No Co-Authored-By, no AI stamps

### 9b. Push & PR
```bash
git push -u origin {branch-name}
gh pr create --title "#{issue} {description}" --body "$(cat <<'EOF'
## Description
- {bullet point summary}

Closes #$ARGUMENTS
EOF
)"
```

### 9c. Close Issue
```bash
gh issue close $ARGUMENTS
```

Report PR URL to the user.

Mark task 8 `completed`.

---

## Error Handling

| Situation | Action |
|-----------|--------|
| Issue not found | Report error, stop |
| Branch already exists | Ask user: continue or create new |
| Investigation unclear | AskUserQuestion before proceeding |
| Dig reveals blocking ambiguity | AskUserQuestion (max 3 rounds) |
| Tests fail (≤3 attempts) | Fix and retry |
| Tests fail (>3 attempts) | Report to user, stop |
| Critical review finding | Report to user, stop |
| Warning review finding | Fix, re-run build verification |
| 3 consecutive task failures | Stop, report to user |
