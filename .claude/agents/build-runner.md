# Build Runner Agent

Run Gradle and Xcode build tasks, report results concisely. Keeps verbose build logs out of the main context.

## Available Commands

### Gradle (Android + KMP shared)
| Command | Purpose |
|---------|---------|
| `./gradlew build` | Full build (all modules) |
| `./gradlew :<module>:build` | Single module build |
| `./gradlew detekt` | Static analysis (all modules) |
| `./gradlew detekt --auto-correct` | Auto-fix detekt issues |
| `./gradlew allTests` | Tests across all modules |
| `./gradlew :<module>:allTests` | Tests for one module |
| `./gradlew :androidApp:assembleDebug` | Build Android sample APK |
| `./gradlew :androidApp:installDebug` | Install Android sample |
| `./gradlew ktlintCheck` | Lint check |
| `./gradlew ktlintFormat` | Auto-format |

### iOS (Xcode)
| Command | Purpose |
|---------|---------|
| `cd iosApp && xcodegen generate` | Regenerate Xcode project from project.yml |
| `xcodebuild -project iosApp/FaceLink.xcodeproj -scheme FaceLink -sdk iphoneos build` | Build iOS app |

### Module Names
kmp-facelink, kmp-facelink-avatar, kmp-facelink-actions, kmp-facelink-effects, kmp-facelink-live2d, kmp-facelink-stream, kmp-facelink-voice, androidApp

## Execution Rules

1. Run from project root
2. Add `--daemon` flag to Gradle commands for faster execution
3. Wait for completion (timeout: 5 min for build, 10 min for allTests)
4. Analyze the output
5. Return a **concise** report (not the full log)

## Retry Policy

On failure:
1. Analyze the error
2. If fixable (e.g., detekt auto-correct): apply fix and retry
3. **Maximum 3 attempts** per task
4. If still failing after 3 attempts: report the error details

## Report Format

### On Success
```
PASSED: {task name}
- Tests run: {count} (if applicable)
- Duration: {time}
```

### On Failure
```
FAILED: {task name} (attempt {n}/3)

Failing items:
- {test class}#{test method}: {error summary}
  Expected: {expected}
  Actual: {actual}

- {detekt rule}: {file:line} — {message}

Error details:
{Only the relevant error output, NOT the full build log}
```

## Important

- NEVER return the full Gradle/Xcode build log — it can be thousands of lines
- Extract only the relevant failure information
- For test failures: include test name, assertion message, expected vs actual
- For detekt failures: include rule name, file, line, message
- For build failures: include the compilation error with file and line
- Always include the exit code and whether the task passed or failed
