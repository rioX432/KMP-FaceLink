---
name: review-pr
description: Review a GitHub pull request for KMP-FaceLink
argument-hint: "[PR number or URL]"
user-invocable: true
disable-model-invocation: true
allowed-tools:
  - Bash(git fetch:*)
  - Bash(git checkout:*)
  - Bash(git diff:*)
  - Bash(git log:*)
  - Bash(git status)
  - Bash(git merge-base:*)
  - Bash(./gradlew detekt)
  - Bash(gh pr view:*)
  - Glob
  - Grep
  - Read
  - Task
  - mcp__codex__codex
  - mcp__codex__codex-reply
  - mcp__gemini-cli__ask-gemini
---

# PR Code Review

Review the specified pull request (number or URL as `$ARGUMENTS`).

## Steps

1. **Get PR info**: Run `gh pr view $ARGUMENTS --json number,title,body,baseRefName,headRefName,files` to get PR metadata and changed files.

2. **Checkout the branch**: Run `git fetch origin <headRefName> && git checkout <headRefName>` to get the actual code.

3. **Read changed files**: Read each changed file in full to understand context — don't rely on diff alone.

4. **Launch reviewers in parallel**: Based on changed file paths, use the Task tool to launch reviewers **in parallel** (single message):
   - `.kt` files in KMP modules → `kmp-reviewer` agent (checks explicitApi, Detekt rules, source set separation, BlendShape naming, thread safety, Flow usage)
   - Any platform files → `platform-checker` agent (checks expect/actual completeness, feature parity, thread safety symmetry)
   - Only launch reviewers for areas that have changes. Pass the list of changed files to each agent.

5. **Cross-Review (Codex)**: Use `mcp__codex__codex` with `sandbox: read-only`:
   - Verify architecture correctness
   - Check for blind spots missed by other reviewers
   - If changes are non-trivial (>5 files or new public API), also use `mcp__gemini-cli__ask-gemini` for a second opinion

6. **Run lint checks**: Run `./gradlew detekt` if Kotlin files changed.

7. **Aggregate results**: Combine findings from all reviewers and lint checks.

## Output Format

```markdown
## Review Summary

**PR:** #{number} — {title}
**Branch:** {head} → {base}
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

### Detekt
{pass / N violations}

### Cross-Review (Codex)
{summary}
```

## Notes

- Do not flag pre-existing issues in unchanged code
- Skip issues that detekt would catch automatically
- Critical findings should be reported immediately without waiting for full review completion
