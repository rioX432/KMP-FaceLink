# 撮影UXパイロット手順

目的は「誰でも使える」と証明することではなく、まず1名が介助なしで撮影導線を進めるか確認すること。顔トラッキング精度はこのパイロットでは判定しない。

## 安全境界

- 最初はAndroidで中立1回だけ確認し、22回の本収録へ直行しない
- 顔画像、動画、landmark、transform、画面録画、顔meshのスクリーンショットを端末外へ出さない
- 記録するのは操作回数、介助の有無、所要時間、画面遷移、エラー種別だけ
- 別の被験者で行う場合は、事前の説明と同意を人間が用意する。このアプリの説明画面は被験者同意の代わりではない
- 撮影開始は参加者が最初の `撮影を始める` を押した後だけ。検証担当者が代わりに押さない

## 短縮パイロット

1. アプリを起動し、端末を参加者へ渡す。検証担当者は画面操作をしない
2. 参加者が最初の説明を読み、`撮影を始める` を押す
3. 画面の1文だけを頼りに、顔を枠へ合わせる
4. 条件が3秒続くと自動で中立テイクを開始する。開始ボタンは無い
5. 22秒の指示に従い、保存後の自動確認が終わるまで待つ
6. 次の撮影画面で `休憩` を押して終了する

## 記録する公開可能な項目

```text
platform / device model / OS:
build git SHA / dirty:
intro confirmation taps:             expected 1
manual start taps:                    expected 0（操作自体が存在しない）
operator explanations after intro:   expected 0
gate correction changes understood:  yes / no
unintended recording before consent: expected 0
recording completed:                  yes / no
preflight completed automatically:   yes / no
pause found without help:             yes / no
technical words or raw errors seen:   expected 0
participant-visible failure and recovery, if any:
elapsed time from intro confirm to pause:
```

## 進行判定

次をすべて満たしたときだけ、同じ人で22回の完走パイロットへ進む。

- 初回確認以外の開始操作が要らない
- 介助なしで位置合わせを直せる
- 失敗時に未保存であることと、次に何を押すかが分かる
- 最初のデータ確認が自動で終わる
- 休憩へ介助なしで入れる
- 参加者画面に研究用語、実装用語、生エラーが出ない

この判定は1名・1端末のパイロット合格であり、一般的なユーザビリティやアクセシビリティの証明ではない。Androidの短縮・完走が通った後、iOSで同じ導線を別に確認する。
