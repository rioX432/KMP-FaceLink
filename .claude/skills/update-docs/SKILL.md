---
name: update-docs
description: "Audit and update project docs — AGENTS.md from module structure, CHANGELOG.md from git history, README sync"
argument-hint: "[target: all | agents | changelog | readme]"
user-invocable: true
disable-model-invocation: true
allowed-tools:
  - Bash(git log:*)
  - Bash(git tag:*)
  - Bash(git show:*)
  - Glob
  - Grep
  - Read
  - Edit
  - Write
  - Task
  - TaskCreate
  - TaskUpdate
  - TaskList
  - AskUserQuestion
  - mcp__codex__codex
  - mcp__codex__codex-reply
---

# /update-docs — Documentation Audit & Update

Sync KMP-FaceLink documentation with current implementation state.

**Target:** "$ARGUMENTS" (all | agents | changelog | readme — default: all)

---

## Phase 0: Parse Arguments

- `agents` → update AGENTS.md only (module table, API table)
- `changelog` → generate/update CHANGELOG.md only
- `readme` → update README.md only
- `all` or empty → run all phases

If argument is empty or unrecognized, use `AskUserQuestion`:

**Q: Which documents should be updated?**
- All documents (agents + changelog + readme) *(Recommended)*
- AGENTS.md — fix outdated module structure or API table
- CHANGELOG.md — generate from git history
- README.md — update features and links

---

## Phase 1: Create Task Tracker

Create tasks based on selected scope:

| Subject | When |
|---------|------|
| "Gather: module structure + docs + git history (parallel scan)" | always |
| "Update AGENTS.md — module table + API table" | agents or all |
| "Generate/update CHANGELOG.md" | changelog or all |
| "Update README.md" | readme or all |
| "Verify internal links" | always |

---

## Phase 2: Parallel Information Gathering

Mark gather task `in_progress`. Launch **3 Task subagents in parallel** (`subagent_type: "Explore"`, `model: "haiku"`).

---

### Subagent A: Module Structure Scanner

```
Scan KMP-FaceLink module structure.

Root: /Users/rio/workspace/projects/KMP-FaceLink/

1. Read settings.gradle.kts — list all included modules
2. For each module, read its build.gradle.kts to find:
   - Which source sets exist (commonMain, androidMain, iosMain, commonTest)
   - Stability (check if described as Experimental/Stable in AGENTS.md)
3. For each module's commonMain, identify public API entry points:
   - Top-level public classes and interfaces
4. Read AGENTS.md — extract current module table and API table

Return:
{
  "modules": [
    {
      "name": "kmp-facelink",
      "gradlePath": ":kmp-facelink",
      "sourceSets": ["commonMain", "androidMain", "iosMain", "commonTest"],
      "stability": "Stable",
      "purpose": "Core tracking (face, hand, body)",
      "publicEntryPoints": ["FaceTracker", "HandTracker", "BodyTracker", "HolisticTracker"]
    }
  ],
  "agentsModuleTable": "copy of current module table from AGENTS.md",
  "agentsApiTable": "copy of current Key Public APIs table from AGENTS.md"
}
```

---

### Subagent B: Docs Scanner

```
Scan documentation in KMP-FaceLink.

Root: /Users/rio/workspace/projects/KMP-FaceLink/

1. Use Glob to find all *.md files
2. Read README.md — extract Features section and any links
3. Check if CHANGELOG.md exists
4. Read AGENTS.md — check module table and API table are up to date
5. Check if all modules listed in AGENTS.md still exist as directories

Return:
{
  "allDocs": ["README.md", "AGENTS.md", "CLAUDE.md", ...],
  "changelogExists": true/false,
  "readmeFeaturesBullets": ["- bullet 1", "- bullet 2", ...],
  "agentsModuleNames": ["kmp-facelink", "kmp-facelink-avatar", ...]
}
```

---

### Subagent C: Git History Scanner

```
Scan git history for CHANGELOG generation.

Root: /Users/rio/workspace/projects/KMP-FaceLink/

1. Run: git tag --sort=-version:refname | head -5
2. If tag exists: git log {latest_tag}..HEAD --pretty=format:"%H|%s|%as" --no-merges
   If no tags: git log --pretty=format:"%H|%s|%as" --no-merges | head -100
3. Classify each commit subject:
   - Added: new feature, new module, "add", "implement", "support"
   - Changed: update, refactor, improve, bump, migrate
   - Fixed: fix, bug, crash, error, broken, wrong
   - Removed: remove, delete, drop
   - Infrastructure: CI, lint, detekt, build, gradle, workflow

Return:
{
  "latestTag": "v1.0.0" or null,
  "commits": [
    {
      "hash": "abc1234",
      "subject": "Add iOS Whisper.cpp cinterop (#108)",
      "date": "2026-02-01",
      "category": "Added"
    }
  ]
}
```

---

## Phase 3: Gap Analysis

Mark gather task `completed`.

Consolidate results:
- Modules in settings.gradle.kts not reflected in AGENTS.md module table
- Public APIs not in AGENTS.md Key Public APIs table
- Module stability mismatch (Experimental marked as Stable, etc.)
- Features not mentioned in README.md
- Unlogged commits since last tag

Present gap summary with `AskUserQuestion`:

**Q: Proceed with updates?**
- Yes, update everything found *(Recommended)*
- Let me review first (show full diff preview)
- Skip specific section

---

## Phase 4: Update AGENTS.md

*(Skip if target ≠ agents, all)*

Mark task `in_progress`.

1. Build updated module table from Subagent A results
2. Build updated Key Public APIs table if new entry points were found
3. Read AGENTS.md first to find exact section boundaries
4. Use `Edit` to replace only the changed sections — do NOT rewrite the whole file

Mark task `completed`.

---

## Phase 5: Generate/Update CHANGELOG.md

*(Skip if target ≠ changelog, all)*

Mark task `in_progress`.

### Format: Keep a Changelog 1.1.0

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- {subject} ({short_hash})

### Changed
- ...

### Fixed
- ...

### Removed
- ...

### Infrastructure
- {CI/lint/build changes}
```

Rules:
- Group commits by category
- Use commit subject as-is, append `({short_hash})`
- Skip merge commits (already filtered by `--no-merges`)
- Infrastructure category goes last
- If tag exists, create a section for each tagged version

Mark task `completed`.

---

## Phase 6: Update README.md

*(Skip if target ≠ readme, all)*

Mark task `in_progress`.

1. Identify modules/features not mentioned in README Features section (from gap analysis)
2. Add missing module descriptions as bullet points
3. Add CHANGELOG.md link if missing and file exists

Mark task `completed`.

---

## Phase 7: Verify Internal Links

Check all relative links in modified files resolve to existing files.
Flag any broken links in the final report.

---

## Phase 8: Summary Report

Mark all tasks `completed`.

```
## /update-docs Complete

Target: {all | agents | changelog | readme}

### Files Modified
| File | Action |
|------|--------|
| AGENTS.md | Updated module table (N changes) |
| CHANGELOG.md | Created ({N} commits) / Updated |
| README.md | Added {N} features |

### Gap Analysis
| Area | Before | After |
|------|--------|-------|
| Missing modules in AGENTS | N | 0 |
| Unlogged commits | N | 0 |
| README missing features | N | 0 |

### Internal Links
All {N} verified links: ✓
```

---

## Error Handling

| Situation | Action |
|-----------|--------|
| Subagent returns no data | Read settings.gradle.kts directly |
| No git tags | Log all commits as [Unreleased] |
| CHANGELOG.md up to date | Note "already current", skip |
| Edit fails (pattern not found) | Read file again, retry with correct pattern |
| AGENTS.md section not found | Ask user before rewriting whole section |
