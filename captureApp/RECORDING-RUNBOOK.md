# 収録手順（KMP-FaceLink Capture / Android）

対象: cheek-puff研究のguided収録。現在の参加者向け画面は、端末容量と漏洩面を抑えるため
**係数・landmark・transformだけを保存し、カメラ画像を保存しない**。

## 0. なぜこの形か（読み飛ばさない）

過去5回の収録で、条件が揃っている軸が1つも無かった（台帳F12）。カメラ・解析解像度・MediaPipe版・実行モード・ミラーが毎回違い、数値を収録間で比べられなかった。

このアプリは、収録条件と保存契約をテイクへ明記し、異なる条件を同じものとして扱わないために作ってある。
現在のguided画面が選ぶstorage profileは`COEFFICIENTS_ONLY_V1`。`framesStored = 0`で、
`frames/`自体を作らない。画像リプレイが必要な研究は、別途レビューした
`CANONICAL_RGBA_240X320_V1`を明示して収録する必要があり、参加者向け画面から暗黙には有効にならない。

収録時に固定する推論条件：

| 項目 | 固定値 | 理由 |
|---|---|---|
| カメラ | **前面** | 製品が前面で顔を追う。censusは背面で撮られていて比較できなかった（E39） |
| ミラー | **あり** | 製品は前面画像を水平反転してから推論する（E47） |
| 実行モード | **LIVE_STREAM** | 製品と同じ。落ちるフレームも測定対象 |
| 解析要求 | 320×240 | 製品と同じ要求値。**実際に来た値**がテイクに記録される |
| モデル | `face_landmarker_v2_with_blendshapes.task` | SHA-256 `64184e22…` = 製品同梱と一致（E32）。実機テストで毎回照合 |

### Session Cの12テイク確認収録は別プロトコル

学習型パッチ候補を確認するSession Cだけは、developer-only intentで
`CHEEK_PUFF_LEARNED_PATCH_CONFIRMATORY_12_V1`を選ぶ。このIDは
`CANONICAL_RGBA_240X320_V1`と凍結12スロットにコード上で結合され、別profile・別順序で同じIDを
作ると開始前に失敗する。画面はsession自身のプロトコルだけを表示し、呼び出し側から別のシナリオ列を
渡せない。登録外シナリオと登録回数を超えた追加テイクも一時ディレクトリ作成前に拒否する。

以下は4視点の同一hashレビュー、exact-binary packet、ownerによる署名済みSession-C認可後にだけ使う。
通常起動は従来の22テイク・係数のみであり、Session Cにはならない。`study_protocol`だけを渡す起動は、
認可不足としてカメラ許可・容量検査・storage作成より前に停止する。

```bash
adb shell am start -S \
  -n io.github.kmpfacelink.capture.app/.CaptureActivity \
  --es study_protocol learned_patch_session_c_v1
```

これは**fail-closed確認専用**であり、収録開始コマンドではない。実際のSession Cには別途発行された
canonical `session-c-execution-authorization-v1`（protocol、profile、source closure、installed APK、pre-binary execution contract、binary receipt、
cheek/mouth control hash、run ID、expiryをP-256で署名）をintentへそのまま渡す必要がある。署名発行手順と
approved exact-binary packetが無い段階では、その認可を作らずこの起動だけで停止を確認する。性能計測の
`facelink-run-authorization-v1`は別目的であり、Session C認可の代わりにはならない。

既存のguided-22 active runが残っている場合、このrouteはそのファイルを削除・上書き・新runへ回転させず停止する。
Session Cの前に、guided-22を取り出して構造検証し、別レビュー済みのnon-destructive transition手順でactive runを
保存・切り替えなければならない。このrunbookはそのtransitionを実行する権限を与えない。

起動時にアプリ自身がcapture containerと同じvolumeの空き容量を検査し、4 GiB未満ならカメラ許可前に
停止する。開始画面が全12回と表示されなければ収録しない。最初の中立テイク後はcanonical-RGBA用preflightが
通るまでslot 2へ進まない。未知の`study_protocol`値はカメラとstorageを開かずfail closedする。

係数のみのテイクからIMAGE / VIDEO・CPU・別解像度の再推論は作れない。再推論できるのは、
`CANONICAL_RGBA_240X320_V1`で明示的に保存したAndroidテイクだけ。過去のschema 3/4テイクは
診断用に読めるが、現在のschema 5のguided進捗にもexact replayにも合格させない。

## 1. 準備（PC側・1回だけ）

このvalidation用`captureApp`の対応端末はAndroid 8.0 / API 26以上。再利用可能な
`kmp-facelink` / `kmp-facelink-capture` SDKのconsumer要件はminSdk 24のままである。

```bash
cd /path/to/KMP-FaceLink
./gradlew :captureApp:installDebug
```

インストール前に、端末が **USBデバッグ有効・画面ロック解除** であること。ロック画面のままだとカメラが起動しない（フォアグラウンドでないアプリはカメラを開けない）。

## 2. 実機での確認（収録前・毎セッション）

```bash
./gradlew :captureApp:connectedDebugAndroidTest
```

3件通ればモデルと形式は健全（**モデルSHA照合・保存フレームの再推論・疎な結果ストリームの結合**）。`CameraPipelineTest` はカメラが前面に出ている必要があるので、ロック解除して実行する。

アプリを開いて位置合わせ画面を見る:

- 最初に表示される説明を読み、`撮影を始める` を1回押す。以後は基本的に自動進行する
- 顔・中央・大きさ・明るさ・距離の5条件を内部で確認する。画面には今直すことだけを1文で表示し、全部そろったまま3秒続くと自動で録画を始める。これが距離・照明・位置を毎テイク同じにする仕組み
- カメラpreviewを主面にして、その上へメッシュと顔型ガイドを重ねる。収録中も旧110dp固定枠へ縮めず、指示・残り秒・停止操作を除いた縦の余白をpreviewへ割り当てる。これはsource/layout契約であり、実機での見やすさ確認ではない
- preview下端の一時診断は `収録` / `入力あり|入力待ち` / `age` / `顔あり|顔なし` / `F C S B D` の5-bitだけを表示する。`F=face`、`C=centered`、`S=sized`、`B=bright`、`D=distanced`。`age`は画面が最後の結果更新を観測してからの秒数（99秒で上限）で、座標・transform・係数・画像は表示、log、保存、exportしない
- `確認のみ` と出るpreview-checkはカメラとgateを確認するだけで、収録を開始しない。現在の既定profileではpreviewに映っても画像ファイルは記録しない
- `カメラ入力を受け取れませんでした`で停止したrunはterminal。遅れて別の失敗結果が届いても再試行へ戻らず、preflight・進捗・撮り直し操作より停止画面を優先する。アプリを開き直すまで同じrunを続けない
- 撮り直し削除の待機中にrunがterminalになった場合、開始済みのディスク削除自体は完了することがあるが、その遅延結果からテイク選択・保存表示・次の位置合わせ・失敗メッセージを更新しない
- 画面の進捗は現在の`runId`・protocol・storage profileがすべて一致するschema 5マニフェストだけから復元される。以前の撮影や別profileのテイクは数えない。保存・削除の完了直後にも画面の件数が更新される
- 開始は必ず3秒の位置合わせ確認を通る。テイクの間は `休憩` でいつでも止められる

## 3. 収録

1. アプリが示す次のテイクを確認する。対象は固定順で自動的に進み、同じシナリオの反復は `1回目` / `2回目` として別々に進捗管理される
2. 顔型ガイドと画面中央の1文に従って位置・距離・明るさを合わせる。条件がそろったまま3秒経つと自動で始まる
3. 画面の指示に従う。全22テイクとも **準備2秒 → 中立4秒 → 保持4秒 → 戻し4秒 → 保持4秒 → 戻し4秒**（計22秒）。固定区間だけで8分04秒あり、位置合わせ・確認・休憩の時間が別に加わる
   - **保持は2回。** 1回で止めない。「反復間で不安定」という所見は2回あって初めて意味を持つ
   - 次の指示は現在区間の最初から表示される。`合図まで動かない` / `合図まで そのまま` に従い、表示が切り替わる前に動かない
   - 収録プロトコル17本の後に、yaw右・yaw左・eye widen・jaw forward・clenchの追加収録5本が続く。画面では `収録プロトコル` / `追加収録` と表示される
   - light・mid・maxの2回目は、blow・pucker・smile・tongue・nose・speechを一巡した後に収録する
   - blow 1回目は保持型、2回目は自然型。画面に出る回別の補足に従う
4. 終了後、`テイクを保存しました` と完了数だけが出て、2秒後に次へ自動で進む。検出値・スコア・フレーム件数は出ない

### 最初の中立テイクだけ自動確認する

最初の中立テイクを保存すると、残りへ進む前にprofileに対応した構造確認を端末内で自動実行する。
利用者が開発者画面を操作する必要はない。

- 既定の係数のみprofileでは、`framesStored = 0`、`frames/`不在、4つの結果streamの長さ・hash、
  manifest件数、sequence・timestampの単調増加を確認する
- 明示的なAndroid canonical-RGBA profileだけ、`IMAGE`と`VIDEO`の再推論、frame/result join、
  face presence一致を追加確認する
- これは表情の良し悪しや推定精度の採点ではない。**選択したstorage profileの契約どおり保存できたか**だけを確認する
- 確認できなければ自動で先へ進まない。`もう一度確認する` または `中立テイクを撮り直す` を選ぶ
- 顔画像・landmark・推論値は端末外へ送らない

### 撮り直し

保存完了画面の `撮り直す` は、直前のテイクを撮り直す近道。以前のテイクを撮り直す場合は `進捗を見る` を開き、対象行の `撮り直す` を押す。どちらもシナリオ名とテイクIDを表示した確認は1回だけで、`削除して撮り直す` を押すと対象テイクをディスクから削除し、同じシナリオの位置合わせへ戻る。

### 片頬は自分の左右で言う

`Puff your own LEFT cheek only` は**被験者自身の左頬**。プレビューはミラーなので、膨らんだ側は画面上では反対に見える。**ここを取り違えたのが、2回の収録で違う頬を膨らませていた原因**（E38）。

### ハードネガティブを飛ばさない

`Pucker` と `Blow` は必須。これが無いと誤検出率が出ず、検出率だけでは検出器の可否を語れない。

## 4. 開発者向けの詳細確認

通常の撮影では、前節の自動確認だけでよい。全22テイク完了後の `開発者向け: 保存データを検証` では、
同じ確認の詳細を表示できる。係数のみprofileでは「画像0件」と結果streamの構造確認が出る。
canonical-RGBA profileで収録したAndroidテイクだけ、IMAGE / VIDEOの2行が出る。

```
20260818-...  [IMAGE] stored=450 live=430 joined=430 faceAgreement=100% blendshape max=0.0xxxxx (...) median=0.00xxxx landmark max=0.00xxxx
20260818-...  [VIDEO] ...
```

読み方:

| 見るもの | 期待 | 外れたときの意味 |
|---|---|---|
| `stored` vs `live` | stored ≥ live | LIVE_STREAM が推論を回さなかったフレームの数。**差があるのは正常** |
| `joined` | live と同じ | 0 や極端に少なければ**結合が壊れている**（保存フレームと結果が対応していない） |
| `faceAgreement` | 100%近い | 低ければ**保存したピクセルが推論の見たものと違う**。ミラー・バッファ・解像度のどれかが壊れている |
| `blendshape max` | 小さい。VIDEO < IMAGE | **一致テストではない**。LIVE_STREAM は追跡できている間 face detector を飛ばす（`face_landmarker_graph.cc`）ので、毎フレーム検出し直す IMAGE との差は実行モードそのものの差 |

この表はcanonical-RGBA profile専用。係数のみprofileへ適用しない。どちらのprofileでも、対応する
構造条件を満たさない限り先へ進めない。詳細値の解釈や性能計測は撮影参加者ではなく、検証担当者が行う。

## 5. テイクの取り出し

```bash
adb exec-out run-as io.github.kmpfacelink.capture.app tar c -C files/capture takes > takes.tar
```

係数のみprofileの1テイク:

```
results.idx          結果の索引（seq・phase・フラグ・時刻）
landmarks.f32        478×3 float
blendshapes.f32      52 float（生値。補正前）
transform.f32        4×4 float
take.json            schema 5のrun identity・条件・件数・区間・storage receipt・4 streamのSHA-256
```

Android canonical-RGBA profileでは、上に加えて次を持つ:

```
frames/frames.rgba   無圧縮RGBA（240×320なら1枚307KB）
frames/frames.idx    フレームの索引（seq・phase・時刻・オフセット）
frames/frames.json   フレーム側マニフェスト（両ファイルのSHA-256込み）
results.idx          結果の索引（seq・phase・フラグ・時刻）
landmarks.f32        478×3 float
blendshapes.f32      52 float（生値。補正前）
transform.f32        4×4 float
```

**`frames.rgba` と `landmarks.f32` は生体情報。** Git・PDF・公開スライドに入れない（`scripts/verify_public_artifacts.py` が検査する）。

### 失敗・再起動時

- 完了したテイクだけが`takes/`へ移動する。寸法確認、sink生成、close、manifest同期、publishの途中で失敗した
  テイクは`tmp/`から削除され、進捗には入らない
- アプリやOSが途中終了して`tmp/`が残った場合は、次回session作成時にcapture-owned `tmp/`直下だけを回収する。
  `takes/`、active run identity、capture root外は削除しない
- 同じcapture rootを2つのlive sessionが同時所有することはprocess内leaseで拒否する。これにより2つ目のsessionが
  1つ目の収録中`tmp/`をstaleと誤認して削除しない。process終了時はOSとともにleaseが解放される
- 回収そのものに失敗した場合はsession開始を失敗させる。容量を消費したまま新しい撮影を続けない
- Androidのsession constructor/start failureは画面上の開始失敗として扱い、Activityをabortさせない
- カメラframe sourceが失敗した場合は全5ゲートをfalseへ戻し、当該runを停止する。テイク単位のretryには戻さず、撮影画面を開き直す。`入力あり`の表示だけを顔検出成功と読まない
- この回復契約はsource/test境界であり、電源断直後のfilesystem durabilityや物理空き容量の保証ではない

## 6. 収録前に済んでいないこと（既知）

- 係数のみprofileの物理端末no-write確認、持続時の容量・発熱・callback continuityは未確認
- Android canonical-RGBAは1テイク873 frame（256 MiB）でfail closedする。Session Cでは4 GiBの事前容量gateを
  実装済みだが、物理端末での表示・停止動作は未確認。通常の参加者向け画面からは選べない
- iOS canonical-RGBAは240×320の向き・mirror・channelを合成fixtureで証明できるconverterが未実装のため、
  現在はfail closed。ネイティブ解像度保存へfallbackしない
- 保存ON/OFFの割り当て量・キュー高水位・callback率・熱比較は別の性能run。source buildや一回の収録成功で代替しない
