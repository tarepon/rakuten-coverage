# 引き継ぎメモ — 最強プラチナハンター (platinum-hunter)

最終更新: 2026-07-19(5G NSA判定修正〜全体レビュー〜JST対応セッションの引き継ぎ)

## プロジェクト概要

楽天モバイルの電波を計測・記録するAndroidアプリ(Play公開中、applicationId: `io.github.tarepon.platinumhunter`)。
Kotlin + Jetpack Compose (Material3) / osmdroid / Room / FusedLocationProviderClient。
minSdk 26 / targetSdk 35 / リリースはR8有効。

## 現在のブランチ状態(最重要)

- **mainがすべての最新。分岐・未マージ・オープンPRなし**。以後の開発はmain一本でよい
- 旧ブランチ `claude/5g-lte-switching-logic-qfkrm8` はmainと同一内容(役目終了)。リモートに残る
  `claude/app-release-prep-bgdo1l` / `feature/monster-category-and-settings` も**マージ済みの残骸**なので削除してよい(要オーナー確認)
- オーナーMacのローカルにも未コミットは残っていない(前回まであったWIPはすべてコミット済み)

## 開発ワークフロー(オーナーのMac)

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # ターミナルにJavaが無いため必須
./gradlew :app:testDebugUnitTest :app:lintDebug   # テスト+Lint(コミット前に必ず)
./scripts/sign_and_install.sh                     # ビルド→署名→実機インストール→検証まで全自動
./gradlew --stop                                  # CLIビルド後の後始末(Studioとのデーモン衝突防止)
```

- 署名パスワードはmacOS **Keychain**から自動取得(`platinum-hunter-ks` / 別キーパスワードは `platinum-hunter-key`)。
  未登録なら対話入力にフォールバック。登録/解除コマンドはスクリプト冒頭コメント参照
- スクリプトはインストール後に**端末上のAPKサイズ一致まで検証**する(「古いAPKを黙って署名・インストール」事故の再発防止。WORKLOG 29-30)
- リモート実行環境(Claude Code on the Web)は**Android SDK取得不可**(dl.google.com遮断)。gradleビルド代替として
  kotlin-compiler-embeddable+Robolectric android-all(Maven Central経由)で「純粋関数テスト実行+実ファイルコンパイル検証」が可能(WORKLOG 24参照)。
  Compose/Room依存ファイルはリモートでは検証不能なので、**最終確認は必ずオーナーのgradleで**

## 5G判定アーキテクチャ(このセッションの中核。触る前に必読)

`NetworkInfoCollector.collect()` (suspend) の判定は3層構造:

1. **セル選定** `selectCellIndexByPlmn`(純関数): 楽天PLMN(440-11)優先、**NR優先**
   (在圏楽天NR → 信号あり楽天NR → 在圏楽天 → 非NR楽天 → 楽天 → 在圏au → au)。
   NSA構成では在圏フラグがLTEアンカー側に立ちNRセルはisRegistered=falseのため、NR優先が必須
2. **NSA補正** `DisplayInfoMonitor` + `applyNrOverride`(純関数): NRセルをセル一覧に出さない端末向けに、
   ステータスバーの5Gアイコンと同一情報源 `TelephonyDisplayInfo.overrideNetworkType` を購読
   (**API 31+はREAD_PHONE_STATE不要** — TelephonyRegistryのcompat changeで確認済み)。
   LTE判定+NR接続中→"5G"に昇格、ただし**バンドはnullに落とす**(アンカーBand 28をプラチナ5G誤判定しないため)。
   昇格は**楽天セル選択時のみ**(auローミング誤昇格防止)。購読は楽天SIMのsubIdに紐付け、SIM入替で張り替え
3. **バンド解決**: CellIdentity.bands(API 30+) → 空ならARFCN逆引き(`nrArfcnToBand`/`earfcnToBand`)

診断: `adb logcat -s SignalDiag` で「セル一覧・選択・nrOverride・最終判定」が1行/計測で出る。
設定→「📶 セル情報の診断」でも実機上で確認可(displayInfoNr行を含む)。

制約(コメント・WORKLOGにも明記):
- API 30以前はDisplayInfo購読に権限が必要なため未購読(セル走査のみ)
- 楽天SIM探索(subId総当たり)は**60秒のネガティブキャッシュ**あり。SIM挿入検出は最大60秒遅れる
- API 29+専用セルヘルパーには`@RequiresApi(Q)`必須(無いとlintDebugが落ちる。WORKLOG 29節の前段)

## 設計方針(変わっていない点)

- 図鑑は**捕獲済みのみ**カウント(`collection_records`)。計測だけでは増えない=仕様
- モンスター生成はseed=`cellId.hashCode()`の決定論。**乱数消費順(SeededRandomのnextInt回数・順序)は変更禁止**
  (既存個体保護。`MonsterGeneratorTest`のgolden-valueテストで固定済み)
- 表示メタデータ(名称・星・絵文字・色・レンジ)は`MonsterCategory` enumに集約。図鑑表示は`SignalLevel.category`経由
- **タイムスタンプは保存=ISO8601 UTC、表示=JST固定**(`util/TimeUtils.formatJst`)。保存形式を変えないこと
  (ソート・重複判定・バックアップ互換・`countBetween`の辞書順比較が依存)。通知の「今日の件数」はJST日界(`jstTodayUtcRange`)
- `MeasurementDao.deleteNonRakuten`は**保守的削除**(楽天/auバンドは残す・carrier NULLは残す)。
  旧バージョンがcarrierにoperatorName生値を保存していた歴史があるため、条件を緩める変更は要注意(WORKLOG 28-1)

## 今セッションでやったこと(詳細: WORKLOG 24〜30)

1. **5G NSA誤判定修正**(実機で解決確認済み): 上記アーキテクチャの構築。旧実装は先頭セル/在圏優先でLTEアンカーを掴んでいた
2. **DUAL SIM対応(main)とのマージ統合**: PLMN選別ベースにNR優先を移植。DisplayInfo購読のDSDS対応
3. **全変更(+2,900行)の8観点マルチエージェントレビュー→検証→修正11件**: データ消失しうる削除クエリ、
   auローミング誤昇格、コールドスタート時の発見演出喪失、メインスレッドbinder IPC、通知固着、購読破壊など(WORKLOG 28に全リスト)
4. **JST表示対応**: 履歴・スタンプ・通知の今日件数
5. **sign_and_install.sh一気通貫化+Keychain署名**

## 次セッションの残タスク・確認事項

1. **実機確認の残り**: ①履歴画面がJST表示か ②コールドスタート後の最初の新セルで発見演出が出るか(28-3の修正確認)
   ③設定「🧹 楽天回線以外の計測を削除」の対象件数が妥当か ④位置情報の事前説明ダイアログ(権限を一度剥がして確認)
2. **レビューで見送った項目**(WORKLOG 28の「見送り」参照):
   - SignalLevel→色/星マッピングがUI4箇所で重複(MapScreen.toColor/badgeRingColor/starCount、HistoryScreen.toComposeColor、CharacterWidget)。
     正規表は`MonsterCategory`にあり、`SignalLevel.category`経由に統一するリファクタ推奨(gradle環境で)
   - rarityRank(地図の重なり順)とstarCount(図鑑の星)の序列不一致(FIVE_G vs PLATINUM)。設計判断が必要
   - `MeasurementService.start()`が権限なし時にfalseを返すが、呼び出し側(MapViewModel:296)が未使用。
     オーナーWIPの続き — 権限なしで開始ボタンを押すと無反応になるため、案内表示につなげるべき
3. **アプリについての問い合わせ先**が未記載(メール or GitHub Issues、オーナー確認のうえ追加)
4. **マネタイズ準備**(着手時): 広告ならUMP同意管理+プラポリ改訂。OSSライセンスは静的リストのため**依存追加時に手動更新が必要**
5. **次回Play提出時**: Play ConsoleでFGS(location)利用申告(RELEASE_CHECKLIST.md)。位置情報の事前説明ダイアログは実装済み(オーナーWIP由来)
6. リモートの残骸ブランチ削除(上記)

## 環境の注意(LESSONS.mdにも記録済み)

- Bash複合コマンドは必ず`cd /絶対パス && ...`で始める(シェルcwdがリセットされ、別リポジトリに誤pushした事故あり)
- DUAL SIM機はデフォルトTelephonyManagerがデータSIM側のセルしか返さない/非データSIMのセルをisRegistered=falseで報告する機種がある
- オーナーのローカルに未コミット変更がある状態が常態化しやすい。**ブランチ切替や取り込みの前に`git status`+`git diff --cached`を確認**
  (ステージ済み変更は`git diff`に出ない。今セッションで2回ハマった)
- APKの中身確認はAPK直grep不可(dexはzip圧縮)。`unzip -o <apk> 'classes*.dex'`してから`LC_ALL=C grep`
- 実機のUIテスト・adb操作の制約はLESSONS.md参照
