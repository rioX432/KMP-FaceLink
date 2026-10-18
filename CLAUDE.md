# CLAUDE.md

Strictly follow the rules in [AGENTS.md](./AGENTS.md).

## Core Values

1. **高精度なフェイス・ハンド・モーショントラッキング** — ネイティブプラットフォーム機能（ARKit/MediaPipe）を活用し、正確で滑らかな追跡を提供
2. **VTuber配信を盛り上げる体験** — トラッキングデータを活用したエフェクト・演出機能で、配信者と視聴者の体験を強化
3. **KMP統一APIでVTuberアプリ開発を簡単に** — Android/iOSのトラッキング→アバター→配信まで、単一コードベースで実装可能

## Won't Do
- **クラウドLLMフォールバック** — LLMモジュールはローカルLLM（Gemma 3n / Apple Foundation Models）のみ。クラウドAPI連携は範囲外。VTuberアプリ側で必要なら自前で実装すべき
- **Desktop GUI application** — KMP-FaceLink is an SDK/library, not an end-user VTuber app. Building a standalone GUI (like VTube Studio or Warudo) is out of scope
- **Community marketplace / item store** — Asset marketplace features (model store, effect store) belong in the application layer, not the SDK
- **Effect rendering engine** — The SDK outputs renderer-agnostic effect parameters (position, intensity, duration). Actual particle/visual rendering is the consumer app's responsibility
- **MCP server for SDK integration** — Premature at current adoption scale. Revisit when developer community reaches critical mass. llms.txt already serves AI-assisted integration
- **CocoaPods distribution** — CocoaPods trunk goes read-only December 2026. SPM via Package.swift is the sole iOS distribution channel

## Think Twice

Before acting, always pause and reconsider. Re-read the requirements, re-check your assumptions, and verify your approach is correct before writing any code.

## Research-First Development (No Guessing)

**Guessing is prohibited.** Never design or implement based on assumptions. Always follow this order:

1. **Investigate first** — Read official docs, inspect source code, or web-search to confirm API signatures, behavior, and best practices.
2. **Self-review** — After designing or implementing, verify consistency with existing patterns, edge cases, and no unverified assumptions.
3. **Cross-review with Codex** — If Codex MCP is available, use it for new module/architecture designs, pattern-deviating implementations, and all code review requests.
4. **Proceed only with confirmed information** — If the source of truth is unclear, investigate further or ask the user.

## Key Gotchas

- Detekt auto-correct reorders imports — run `./gradlew detekt` **twice** if the first run changes import order
- Detekt LongMethod limit is **60 lines** — split functions early
- Detekt ImportOrdering: `kotlinx.*` belongs to the `*` group — must come **BEFORE** `java.*`/`javax.*` (layout: `*,java.*,javax.*,kotlin.*,^`)
- `explicitApi()` enforced — all public symbols **must** have explicit visibility modifiers (`public`, `internal`, etc.)
- iOS: XcodeGen regenerates project from `project.yml` — run `xcodegen generate` after adding new Swift files to iosApp
- iOS: After `xcodegen generate`, re-set the signing team in Xcode
- iOS device ID changes between sessions — always check with `xcrun xctrace list devices`
- SKIE: K/N adds `do` prefix to `init`-prefixed functions (e.g. `initKoin` → `doInitKoin`), but SKIE generates its own Swift async extension without the prefix
- Android build target device: `adb connect <device-ip>:5555` (check the address each session)

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
