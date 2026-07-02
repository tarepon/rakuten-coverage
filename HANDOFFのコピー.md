# 引き継ぎメモ — Rakuten Coverage App

## プロジェクト概要

楽天モバイルの電波強度を計測・記録する Android アプリ（個人用プロトタイプ）。  
Kotlin + Jetpack Compose (Material3) / OSMDroid / Room / FusedLocationProviderClient。

---

## 現在の設計方針（重要）

### トップ画面（マップ）
- カウントバーは **計測累計**（`measurements` テーブル）
- 圏外カウントには `signalLevel == NO_SIGNAL` の他、`rttMs < 0` の計測も含む
- マップドットはカテゴリ色のみの○（絵文字なし）、ズームに連動してサイズ変化

### 図鑑（コレクション）
- **捕獲済みのみ** カウント（`collection_records` テーブル）
- 計測しても捕獲しなければ0匹のまま → **これは仕様**（ゲーム要素の根幹）
- 捕獲方法：手動捕獲ボタン or オートキャプチャON

### モンスター生成
- seed = `cellId.hashCode()` のみ → 同じセルは常に同じモンスター
- cellId = 基地局ID（LTE: ci / NR: nci）。NO_SIGNAL は 0.002°≈222m グリッド `"nosig:lat,lng"` で代替

---

## ファイル構成（主要）

```
rakuten-coverage/app/src/main/java/com/example/rakutencoverage/
├── data/
│   ├── Measurement.kt          # measurements エンティティ + SignalLevel enum
│   ├── CollectionRecord.kt     # collection_records エンティティ + latLngToCellId()
│   ├── CollectionDao.kt        # observeAll / observeAllH3Indexes / observeNoSignalCount
│   └── monster/
│       └── MonsterGenerator.kt # LCG シード乱数でモンスター決定論生成
├── ui/
│   ├── map/
│   │   ├── MapViewModel.kt     # 計測ループ・モンスター発見・捕獲ロジック
│   │   └── MapScreen.kt        # OSMDroid + Compose UI・FABカラム
│   └── collection/
│       ├── CollectionViewModel.kt
│       └── CollectionScreen.kt
└── MainActivity.kt
```

---

## SignalLevel 一覧・表示設定

| SignalLevel | 表示名 | 絵文字 | 色 | レア度 |
|---|---|---|---|---|
| MILLIMETER_WAVE | 5Gミリ波モンスター | 👑 | #FF6F00 オレンジ | ★★★★★ 伝説 |
| PLATINUM | プラチナLTEモンスター | 😊 | #AB47BC 紫 | ★★★☆☆ アンコモン |
| FIVE_G | 5G Sub6モンスター | 😆 | #1E88E5 青 | ★★★☆☆ アンコモン |
| LTE | LTEモンスター | 🙂 | #43A047 緑 | ★☆☆☆☆ コモン |
| WEAK | auの方から来たモンスター | 🤑 | #5D4037 ダークブラウン | ★★☆☆☆ 他社エリアに間借り中 |
| NO_SIGNAL | 圏外の亡霊 | 💀 | #212121 黒 | ★★★☆☆ 闇レア |

カウントバー表示順: 5Gmm → PtLTE → 5G → LTE → KDDI → 圏外

---

## MapViewModel の主要ロジック

```kotlin
// 新規セル検出時の処理（runSingleMeasurement 内）
if (cellId != currentCellId) {
    currentCellId = cellId
    _discoveredMonster.value = null
    if (result.signalLevel.isCollectable && cellId !in capturedCellIds.value) {
        val monster = MonsterGenerator.generate(cellId, 1, result.signalLevel)
        if (_autoCapture.value) {
            autoCapture(monster, result)   // DB保存 + 4秒カード表示
        } else {
            _discoveredMonster.value = monster
            vibrateShort()                 // 発見バナー + バイブ
        }
    }
}
```

- `capturedCellIds`: `HashSet<String>` として O(1) 検索。`collection_records` を監視。
- `captureMonster()`: 手動捕獲。DB upsert → `_capturedMonster` セット → 7秒後クリア。
- `vibrateShort()`: 「ト・トン」3サイクル（200ms振動 + 200ms無音 + 800ms振動 + 1750ms無音）

---

## 図鑑画面構成

### 光図鑑
MILLIMETER_WAVE / PLATINUM / FIVE_G / LTE

### 闇図鑑（サブタイトル: 圏外＝エリア拡張の伸び代を記録せよ）
WEAK / NO_SIGNAL

### 圏外クエスト
NO_SIGNAL 捕獲数に連動したクエスト（1 / 5 / 20 / 50箇所）

---

## FABカラム（ボタン一覧）

上から順：自動捕獲スイッチ / チェックイン / 現在位置 / マッピング間隔 / マッピング開始停止

---

## 未解決・検討事項

- 自動捕獲スイッチのデフォルト値（現在 OFF）
- 圏外モンスターの捕獲率向上策（ゲームバランス上の検討）
- KDDI カウントの扱い（countbar の rttMs<0 含む集計の見直し検討中）

---

## DB バージョン

Room DB version: 4（`AppDatabase.kt` 参照）
