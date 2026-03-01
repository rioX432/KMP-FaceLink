---
name: investigate
description: "Investigate a feature or issue and propose an implementation approach (no implementation)"
argument-hint: "[GitHub issue number or topic]"
user-invocable: true
disable-model-invocation: true
allowed-tools:
  - Bash(gh issue view:*)
  - Glob
  - Grep
  - Read
  - Task
  - Skill
  - ToolSearch
  - AskUserQuestion
  - mcp__codex__codex
  - mcp__codex__codex-reply
---

# /investigate — KMP Architecture Investigation

Investigate and propose an approach without implementing.

**Target:** "$ARGUMENTS"

---

## Step 1: Gather Context

If argument is a number:
```bash
gh issue view $ARGUMENTS --json title,body,labels,comments
```

If argument is a topic, use it directly.

## Step 2: Deep Investigation (kmp-investigator subagent)

Delegate to `kmp-investigator` agent via `Task` tool:

```
Task(
  subagent_type: "general-purpose",
  model: "sonnet",
  prompt: <include issue/topic details, instruct to follow .claude/agents/kmp-investigator.md>
)
```

The investigator traces:
- commonMain API → expect/actual → platform implementations
- Module dependencies involved
- Platform asymmetries
- Existing tests

Only the structured report enters our context.

## Step 3: Ambiguity Analysis (/dig)

Run `/dig` skill to identify and resolve decision points:
- Source set placement decisions
- Module placement decisions
- expect/actual vs interface decisions
- API design decisions

## Step 4: Cross-Check (Codex)

Use `mcp__codex__codex` with `sandbox: read-only` to verify:
1. Is the analysis correct?
2. Are there alternative approaches?
3. Any missed impact areas?

## Step 5: Propose Approach

Synthesize results into a proposal:

```markdown
## Investigation Report: {topic}

### Current Architecture
{kmp-investigator summary}

### Decisions Made
{dig Decision Matrix}

### Proposed Approach
1. {step with file paths}
2. {step with file paths}
...

### Affected Files
| File | Change Type | Description |
|------|------------|-------------|
| path/to/file.kt | modify/create | what changes |

### Risks
- {risk}

### Open Questions
- {if any remain from dig}

### Cross-Check (Codex)
{summary}
```

## Notes

- Do NOT start implementation — this command is for investigation only
- If ambiguities remain, recommend using `/dig` interactively
- Reference AGENTS.md for architecture conventions
