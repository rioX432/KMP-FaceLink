# CLAUDE.md

Strictly follow the rules in [AGENTS.md](./AGENTS.md).

## Language

- **All generated content must be in English**: code, comments, commit messages, PR titles/descriptions, documentation, and TASKS.md entries.
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

- **Read [TASKS.md](./TASKS.md) at the start of every session**
- Pick the next TODO task (highest priority among status = TODO) before starting work
- On task completion, do both:
  1. Update the task's status to `DONE` in `TASKS.md`
  2. Close the corresponding GitHub Issue (`gh issue close <number>`)
