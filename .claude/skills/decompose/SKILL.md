---
description: "Break a task into KMP-layered subtasks: commonMain → expect → androidMain → iosMain → tests → sample app"
---

# Task Decomposition for KMP-FaceLink

Break down a development task into small, executable subtasks following KMP architecture layers.

## When to Use

- After investigation and dig phases are complete
- When the approach is confirmed and decisions are made
- Before starting the implementation loop

## Input Requirements

You need these before decomposing:
1. Investigation results (affected modules, data flow, impact)
2. Decision matrix from dig phase (resolved ambiguities)
3. Confirmed approach

## Decomposition Rules

### Size
- Each task should take **5-30 minutes** to implement
- If a task feels larger, split it further

### Structure per Task
Each task must include:
- **What**: What to implement/change
- **Where**: Target file(s) with paths
- **How**: Specific implementation approach
- **Why**: Reason this change is needed
- **Verify**: How to confirm it works

### KMP Layer-Based Decomposition

Split by architecture layer, inner to outer:

```
1. commonMain API — data classes, interfaces, enums (public, explicit visibility)
2. expect declarations — only if platform-specific implementation needed
3. androidMain actual — MediaPipe/CameraX/Android-specific code (internal)
4. iosMain actual — ARKit/Vision/AVFoundation-specific code (internal)
5. commonTest — unit tests for shared logic, mappers, filters
6. Android sample app — Compose UI in androidApp/ (if visible output)
7. iOS sample app — SwiftUI in iosApp/ + project.yml update (optional)
8. Cross-cutting — detekt + explicitApi compliance + platform-checker
```

### Pairing Rules
- **expect + actual (both platforms) = 1 task** when the actual implementations are simple
- **androidMain actual + iosMain actual = separate tasks** when platform internals are complex
- **Data class + mapper = 1 task**: Type and its transformation go together
- **Test = paired with implementation**: Never separate implementation from its test
- **Cross-cutting checks = last task**: After all implementations are done

### Dependency Order
Set `addBlockedBy` on tasks that depend on earlier ones:
- commonMain API tasks block expect/actual tasks
- expect/actual tasks block platform implementation tasks
- All implementation tasks block test tasks
- All tasks block cross-cutting checks

### Multi-Module Tasks
When multiple modules are affected:
1. Order by dependency tree (core first, extensions after)
2. Complete one module's layer stack before moving to the next
3. Exception: if modules share a new data type, define it in core first

## TaskCreate Format

Use `TaskCreate` for each subtask:

```
subject: "Implement {What} in {Where}"
description: |
  **What**: {description}
  **Where**: {file path(s)}
  **How**: {implementation approach}
  **Why**: {reason}
  **Verify**: {verification step}
activeForm: "Implementing {What}"
```

## Output

After creating all tasks:

1. Show the full task list with dependencies:
```
| # | Layer | Module | Description | Blocked By |
|---|-------|--------|-------------|------------|
| 1 | commonMain | kmp-facelink | Define FooData data class | — |
| 2 | expect | kmp-facelink | Add expect fun createFoo() | #1 |
| 3 | androidMain | kmp-facelink | MediaPipe-based FooTracker | #2 |
| 4 | iosMain | kmp-facelink | ARKit-based FooTracker | #2 |
| 5 | commonTest | kmp-facelink | Unit tests for FooData | #1 |
| 6 | cross-cutting | — | detekt + explicitApi + platform-checker | #3, #4 |
```

2. Show the decision matrix from dig phase
3. Ask user to confirm before starting implementation
