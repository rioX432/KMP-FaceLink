# Task List

GitHub Issues と連動したタスクリスト。
作業完了したら status を `DONE` に変更し、GitHub Issue も close すること。

## Foundation（基盤整備）

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 5 | [CI/CD with GitHub Actions](https://github.com/rioX432/KMP-FaceLink/issues/5) | DONE | GitHub Actions CI + detekt 導入 |
| 10 | [KDoc API documentation](https://github.com/rioX432/KMP-FaceLink/issues/10) | DONE | Dokka + 全 public API の KDoc |
| 4 | [Publish to Maven Central](https://github.com/rioX432/KMP-FaceLink/issues/4) | TODO | maven-publish + XCFramework 配布 |

## Face Tracking 改善

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 3 | [One Euro Filter](https://github.com/rioX432/KMP-FaceLink/issues/3) | DONE | 適応的スムージングフィルタ追加 |
| 9 | [MediaPipe blend shape accuracy](https://github.com/rioX432/KMP-FaceLink/issues/9) | TODO | 低精度パラメータ改善 |
| 12 | [Thread safety](https://github.com/rioX432/KMP-FaceLink/issues/12) | TODO | 並行処理の安全性強化 |

## iOS

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 11 | [SKIE plugin](https://github.com/rioX432/KMP-FaceLink/issues/11) | TODO | Swift-Kotlin Flow interop |
| 2 | [iOS sample app](https://github.com/rioX432/KMP-FaceLink/issues/2) | TODO | Xcode プロジェクト構築 |

## Multi-Tracking（将来）

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 6 | [Hand tracking (Phase 2)](https://github.com/rioX432/KMP-FaceLink/issues/6) | TODO | 手のランドマーク + ジェスチャー |
| 7 | [Body tracking (Phase 3)](https://github.com/rioX432/KMP-FaceLink/issues/7) | TODO | 姿勢推定 |
| 8 | [Holistic tracking (Phase 4)](https://github.com/rioX432/KMP-FaceLink/issues/8) | TODO | Face + Hands + Body 統合 |
