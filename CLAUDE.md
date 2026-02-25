# CLAUDE.md

Strictly follow the rules in [AGENTS.md](./AGENTS.md).

## Language

- **All generated content must be in English**: code, comments, commit messages, PR titles/descriptions, and documentation.
- The only exception is conversation with the user (follow the user's language preference).

## Design & Planning Workflow

When designing, planning, or making implementation decisions:

1. **Fact-based decisions only** — never guess or assume. Base all decisions on actual code, documentation, or verified information.
2. **Verify unknowns** — if facts are unclear, read the relevant code (`Read`/`Glob`/`Grep`) or use web search before proceeding.
3. **Self-review** — after completing design or implementation, review your own output for correctness, consistency with existing patterns, and missed edge cases.
4. **Cross-review (Codex × Claude)** — after self-review passes, run a cross-review between Codex (`mcp__codex__codex`) and Claude to catch blind spots. Apply this to:
   - New module or architecture decisions
   - Non-trivial implementation logic
   - Any design that deviates from existing patterns

## Task Management

- Use GitHub Issues as the single source of truth for task tracking
- At the start of each session, check open issues: `gh issue list --state open`
- Pick the next issue by priority (labels, milestone, or issue number order)
- On completion, close the issue: `gh issue close <number>`
