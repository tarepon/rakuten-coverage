# 引き継ぎメモ — 最強プラチナハンター (platinum-hunter)

最終更新: 2026-07-19（モンスターカテゴリ導入〜DUAL SIM対応セッションの引き継ぎ）

## プロジェクト概要

楽天モバイルの電波を計測・記録するAndroidアプリ（Play公開中、applicationId: `io.github.tarepon.platinumhunter`）。
Kotlin + Jetpack Compose (Material3) / osmdroid / Room / FusedLocationProviderClient。

- テスト: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest`
- 実機インストール: `scripts/sign_and_install.sh`（リリース署名。debug APKは署名不一致で入らない）

## 現在のブランチ状態（最重要）

- **ローカルmain = `feature/monster-category-and-settings` = 9ada517**（今セッションの28コミット）
- **origin/mainは28コミット遅れ**。PR #1（https://github.com/tarepon/platinum-hunter/pull/1）がレビュー待ち
- PRをマージ方式（merge commit/squash）で取り込むとローカルmainと分岐する。**マージ後に`git pull`で整合させるか、ローカルmainを直接pushしてPRをクローズするか**をオーナーと確認すること
- 未コミットで残っているのは**オーナーの通知アイコン関連WIP**（PRIVACY_POLICY.md / RELEASE_CHECKLIST.md / MainActivity.kt / MeasurementService.kt / CheckInPhoto.kt / settings.gradle.kts）。**勝手にコミット・restoreしないこと**。混在ファイル（MainActivity等）に変更を入れる場合はzero-contextパッチの部分ステージ（`git apply --cached --unidiff-zero`）で自分の行だけ分離する（今セッションで3回実施済みの手法）

## 今セッションでやったこと（詳細: WORKLOG.mdセクション23・PR #1本文・docs/superpowers/）

1. **モンスターカテゴリ導入**: `MonsterCategory` enum＝幻(n28)/伝説(LTE Band28)/精鋭(5G Sub6)/通常(LTE)/親切な隣人(auローミング)/通常・悪(圏外)。カテゴリ別ステータスレンジ＋技プール6×10種。`MILLIMETER_WAVE`(n257)は全面削除（n257計測時はFIVE_G扱い）
2. **図鑑**: 幻セクション表示漏れ修正（`collectionLightLevels`＋網羅テスト`CollectionLevelsTest`）、縦一列コンパクトタイル化
3. **設定再編**: エクスポートタブ→「設定」タブ（右端）。アプリ設定/マッピング設定/データ管理/アプリについての4セクション。マップ内の設定シートは撤去済み
4. **DUAL SIM対応**（4段階、WORKLOG 23参照）: ①requestCellInfoUpdateで毎計測リフレッシュ ②「現在の電波」表示をDB最新行から`MeasurementController.latestMeasurement`(メモリFlow)へ ③在圏セルのPLMNフィルタ（楽天440-11/au 440-50系） ④**楽天SIM専用TelephonyManager**（simOperator総当たりでsubId特定、READ_PHONE_STATE追加なし）。DUAL SIM実機の診断で楽天セル取得を確認済み
5. **圏外仕様変更**: 擬似セルグリッド0.002度(222m)→**0.005度(約555m)**。クエスト文言を「捕獲せよ」に統一。闇図鑑ヘッダーに定義明記
6. **アプリについて**: バージョン(動的取得)/作者WILL/非公式免責/プラポリリンク(GitHub)/OSSライセンス静的リスト。地図上のOSM帰属表示(CopyrightOverlay)は既存実装あり
7. **診断機能**: 設定→「📶 セル情報の診断」でセル一覧(PLMN/在圏/ARFCN)を生表示（DUAL SIM切り分け用に常設）
8. **削除機能**: 設定→「🧹 楽天回線以外の計測を削除」（carrier基準＋機内モード等。圏外記録は残す）

## 設計方針（変わっていない点）

- 図鑑は**捕獲済みのみ**カウント（`collection_records`）。計測だけでは増えない＝仕様
- モンスター生成はseed=`cellId.hashCode()`の決定論。**乱数消費順(SeededRandomのnextInt回数・順序)は変更禁止**（既存個体保護。`MonsterGeneratorTest`のgolden-valueテストで固定済み）
- 表示メタデータ（名称・星・絵文字・色・レンジ）は`MonsterCategory` enumに集約。図鑑表示は`SignalLevel.category`経由

## 次セッションの残タスク・確認事項

1. **実機確認（オーナー依頼）**: DUAL SIM機で計測→履歴が「LTE Band 3 / Rakuten」になるか。OKなら「🧹 楽天回線以外の計測を削除」でドコモ誤記録を掃除（約110件想定）
2. **PR #1のマージ判断**（上記ブランチ状態を参照）
3. **アプリについての問い合わせ先**が未記載（メール or GitHub Issues、オーナー確認のうえ追加）
4. **マネタイズ準備**（着手時）: 広告ならUMP同意管理＋プラポリ改訂。OSSライセンスは静的リストのため**依存追加時に手動更新が必要**
5. **既知の技術的負債**: MapScreen.kt:1277付近に図鑑と別系統の`starCount()`重複実装（今回スコープ外、要リファクタ検討）
6. 旧222mグリッドの圏外捕獲レコードは主キーが異なるためDBに残る（クエストカウントに含む。許容済み）
7. 5Gバンドはbands空の機種向けにARFCN逆引き実装済みだが、**5G実測での動作確認は未**（楽天5Gエリアで要確認）

## 環境の注意（LESSONS.mdにも記録済み）

- Bash複合コマンドは必ず`cd /絶対パス && ...`で始める（シェルcwdが毎回リセットされ、親のblog-opsに誤pushした事故あり）
- DUAL SIM機はデフォルトTelephonyManagerがデータSIM側のセルしか返さない／非データSIMのセルをisRegistered=falseで報告する機種がある
- 実機のUIテスト・adb操作の制約はLESSONS.md参照
