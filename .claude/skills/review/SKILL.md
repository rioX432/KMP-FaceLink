---
name: review
description: "KMP code review: kmp-reviewer + platform-checker + Codex cross-review"
argument-hint: "[base-branch, default: master]"
user-invocable: true
disable-model-invocation: true
allowed-tools:
  - Bash(git diff:*)
  - Bash(git log:*)
  - Bash(git status)
  - Bash(git merge-base:*)
  - Glob
  - Grep
  - Read
  - Task
  - ToolSearch
  - mcp__codex__codex
  - mcp__codex__codex-reply
  - mcp__gemini-cli__ask-gemini
---

# /review — KMP Multi-Agent Code Review

Review current branch changes with KMP-specific checks.

**Base branch:** "$ARGUMENTS" (default: master)

---

## Step 1: Identify Changes

```bash
git diff $(git merge-base HEAD ${ARGUMENTS:-master})..HEAD --name-only
```

Categorize changed files:
- commonMain files
- androidMain files
- iosMain files
- Gradle/build files
- Sample app files

## Step 2: KMP Review (kmp-reviewer agent)

Delegate to `kmp-reviewer` agent:

```
Task(
  subagent_type: "kmp-reviewer",
  prompt: <provide changed .kt file list, instruct to review per .claude/agents/kmp-reviewer.md>
)
```

Checks: explicitApi, Detekt rules, source set separation, BlendShape naming, thread safety, Flow usage.

## Step 3: Platform Parity Check (platform-checker agent)

Delegate to `platform-checker` agent:

```
Task(
  subagent_type: "platform-checker",
  prompt: <check expect/actual completeness and feature parity on changed files>
)
```

Checks: expect/actual completeness, feature parity, platform pattern correctness, thread safety symmetry.

Launch Steps 2 and 3 **in parallel** (single message).

## Step 4: Cross-Review (Codex)

Use `mcp__codex__codex` with `sandbox: read-only`:

```
Verify the following code review findings for a Kotlin Multiplatform project.

## Changed files
{file list}

## Diff
{diff}

## KMP Review findings
{kmp-reviewer output}

## Platform Parity findings
{platform-checker output}

Evaluate each finding:
1. Is it technically correct?
2. Is the severity appropriate?
3. Any missed issues?
4. Agree or disagree, with brief reasoning.
```

**Handling:**
- Codex agrees → include as-is
- Codex disagrees → re-evaluate, mark "(Codex disagreed)"
- Codex finds additional → validate and add

## Step 5: Optional Gemini Second Opinion

If changes are non-trivial (>5 files or new public API):
- Use `mcp__gemini-cli__ask-gemini` for additional perspective

## Step 6: Synthesize Report

```markdown
## Review Summary

**Branch:** {current} → {base}
**Files changed:** N (X commonMain, Y androidMain, Z iosMain)
**Risk level:** Low / Medium / High

### Critical (must fix)
- [file:line] {finding} — source: {kmp-reviewer/platform-checker/codex}

### Warning (should fix)
- [file:line] {finding}

### Suggestion (nice to have)
- [file:line] {finding}

### Platform Parity
| Check | Status |
|-------|--------|
| expect/actual completeness | PASS/FAIL |
| Feature parity | PASS/FAIL |
| Thread safety symmetry | PASS/FAIL |

### Cross-Review (Codex)
{summary}

### Cross-Review (Gemini) [if applicable]
{summary}
```

## Notes

- All review logic for KMP checks lives in `.claude/agents/kmp-reviewer.md`
- All platform parity logic lives in `.claude/agents/platform-checker.md`
- This command handles orchestration: diff → agents → Codex → output
- Do not flag pre-existing issues in unchanged code
- Skip issues that detekt/lint would catch automatically
