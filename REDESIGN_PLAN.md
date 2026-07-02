# デザイン刷新プラン — ポケモンGO風モダンUI

対象: `rakuten-coverage/app/src/main/java/com/example/rakutencoverage/`
方針: **ロジック（ViewModel・DB・計測）は一切変更しない。UI層（Compose）のみ刷新。**

---

## コンセプト

ポケモンGOの画面文法に寄せる:
- マップが主役。UIは**画面の縁に浮かぶ半透明の丸いパーツ**に集約
- 四角いカードの積み重ね（現状）→ **ピル型・サークル型のフローティングUI**
- 情報は普段隠し、タップで展開（ボトムシート）
- ダークトーンの地図に映えるビビッドなアクセントカラー

---

## Step 1: テーマ・カラー刷新（ui/theme/）

```kotlin
// ポケGO風パレット
val GoBlue      = Color(0xFF29B6F6)   // メインアクション（捕獲・追従）
val GoDarkNavy  = Color(0xFF1A2B3C)   // パネル背景（半透明で使う）
val GoWhite     = Color(0xFFF5F7FA)   // テキスト
val GoAccent    = Color(0xFFFFC107)   // レア・強調
```

- Material3 `darkColorScheme` ベースに統一（マップ画面は常時ダークUI）
- パネル背景は `GoDarkNavy.copy(alpha = 0.85f)` + `RoundedCornerShape(24.dp)`

## Step 2: トップバー → 「トレーナーバッジ」化

現状: CharacterWidget（幅92%の四角カード）が最上部に鎮座
変更:
- **左上に円形バッジ（56dp）** のみ表示: 現在の SignalLevel の絵文字 + レベル色のリング（`Canvas` で `drawArc`）
- タップで展開: 現在の CharacterWidget の内容（キャラ・コメント）を `AnimatedVisibility` のカードでバッジ下に表示、5秒 or 再タップで収納
- `SignalCountBar` は削除し、**右上のピル型カウンター**（例: `👑3 💎12 🙂45`、捕獲済み上位3種のみ）に縮小。タップでボトムシート（全カテゴリの計測累計テーブル）

## Step 3: 下部UI → ポケGO式レイアウト

現状: MeasurementStatusCard（横長カード）+ FabColumn（ラベル付きFAB縦列）
変更:
- **中央下に大きな円形メインボタン（72dp）**: マッピング開始/停止（▶/⏹）。実行中は `infiniteRepeatable` のパルスアニメーション（外周リングが拡大＋フェード）
- **左下に円形サブボタン（52dp）**: 図鑑ショートカット（既存BottomBarと重複するなら現在位置ボタンに）
- **右下に円形サブボタン（52dp）**: 現在位置（追従中は GoBlue で発光）
- その他ボタン（チェックイン・間隔・自動捕獲）は**右下ボタン長押し or 「⋯」ボタン**で扇状に展開（`animateFloatAsState` で radial menu）。実装コスト高なら縦に `AnimatedVisibility` で展開でも可
- MeasurementStatusCard は削除し、メインボタン上の**細いピル**（例: `📶 LTE • Band 3 • -95dBm`）1行のみ。タップでボトムシート（詳細）

## Step 4: 発見・捕獲演出

- DiscoveryBanner → **画面中央のモンスター出現カード**:
  - `scaleIn + fadeIn` で登場、モンスター絵文字を 64sp で大きく
  - 背景に放射状グラデーション（`Brush.radialGradient`、レア度色）
  - 捕獲ボタンはポケボール風の円形ボタン（「捕まえる！」）
- CapturedMonsterCard → 捕獲成功時:
  - 「⭐ GET!」テキストを `animateFloatAsState` でポップ
  - レア度の星を1個ずつ遅延表示（`LaunchedEffect` + delay）

## Step 5: マップドット強化

- 現状の単色○に **同色の外周グロー**（半径1.6倍・alpha 0.25 の円を下に描画）
- 最新の計測地点だけ**パルスする輪**（ポケGOの自分位置風）
- 自位置マーカー: 青い円 + 白縁 + 進行方向の扇形（bearing が取れれば）

## Step 6: 図鑑画面（CollectionScreen）

- カテゴリカードを**横スクロールの大きなタイル**（160dp角、背景にカテゴリ色のグラデーション + 大きな絵文字）に変更
- 光図鑑/闇図鑑のセクションヘッダはそのまま
- モンスター一覧シートは**2列グリッド**（`LazyVerticalGrid`）、各セルに絵文字大 + 名前 + 星

---

## 実装順序（コミット単位）

1. テーマ・カラー（Step 1）— 影響範囲が全画面なので最初に
2. 下部UI刷新（Step 3）— 使用頻度最高
3. トップバー刷新（Step 2）
4. 発見・捕獲演出（Step 4）
5. マップドット（Step 5）
6. 図鑑（Step 6）

各Stepごとにビルド確認 (`./gradlew assembleDebug`) してからコミット。

## 制約（厳守）

- MapViewModel / CollectionViewModel / DAO / Entity は変更しない
- 既存の機能（チェックイン・間隔選択・自動捕獲スイッチ・追従）は全て残す
- 横画面レイアウト（LandscapeMapLayout）も同じデザイン言語で追随させる
- 文言（「auの方から来たモンスター」等）は変更しない
