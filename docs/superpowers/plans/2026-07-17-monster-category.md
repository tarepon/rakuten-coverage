# モンスターカテゴリ（テーマ）付与 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** signalLevel（電波品質）ごとに伝説・幻・精鋭・通常・ロケット団・通常(悪)のカテゴリ（`MonsterCategory`）を付与し、モンスターのパラメータレンジ（HP/攻撃/防御）と技プールをカテゴリ別に変える。あわせて実質捕獲不可能な `MILLIMETER_WAVE`（5Gミリ波）区分を削除する。

**Architecture:** `SignalLevel` の拡張プロパティ `category: MonsterCategory?` を新設し、`MonsterCategory` enum自身に表示メタデータ（表示名・レア度ラベル・星数・絵文字・色）とパラメータレンジ（HP/攻撃/防御の`IntRange`）を持たせる。`MonsterGenerator` はカテゴリのレンジと専用技プールを使ってモンスターを生成し、`CollectionScreen.kt` の表示ロジックはカテゴリのプロパティを参照するだけにする（ロジックの重複排除）。

**Tech Stack:** Kotlin, JUnit4（`app/src/test`配下のJVMユニットテスト）

## Global Constraints

- 参照設計書: `docs/superpowers/specs/2026-07-17-monster-category-theme-design.md`
- enum命名は `MonsterTheme` ではなく `MonsterCategory` を使う（既存の Compose `ui.theme` パッケージとの混同回避、ユーザー承認済み）
- `SignalLevel` の Room 永続化は `SignalLevel.name` の文字列保存で、読み込み側は既に `runCatching { SignalLevel.valueOf(...) }.getOrNull()` で失敗時 null 除外している（`CollectionViewModel.kt:43,72`）。`MILLIMETER_WAVE` 削除後に旧文字列のレコードがあっても単に一覧から消えるだけでクラッシュしない。**フォールバック処理の追加は不要**（設計書の想定より対応が軽くなった）
- 乱数消費順は変えない（`MonsterGenerator.SeededRandom` の `nextInt()` 呼び出し回数・順序を維持し、bound/offsetだけ変える）
- ビルド確認は `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest` を使う（[LESSONS.md](../../../../LESSONS.md) 記載のJava環境）

---

## File Structure

- 新規 `app/src/main/java/com/example/rakutencoverage/data/monster/MonsterCategory.kt` — `MonsterCategory` enum（表示メタデータ＋パラメータレンジ）、`SignalLevel.category` 拡張プロパティ
- 新規 `app/src/test/java/com/example/rakutencoverage/data/monster/MonsterCategoryTest.kt`
- 修正 `app/src/main/java/com/example/rakutencoverage/data/Measurement.kt` — `SignalLevel` enumから `MILLIMETER_WAVE` 削除、`resolveSignalLevel()` のn257分岐削除
- 新規 `app/src/test/java/com/example/rakutencoverage/data/MeasurementTest.kt`
- 修正 `app/src/main/java/com/example/rakutencoverage/data/CollectionRecord.kt` — `rarityRank` から `MILLIMETER_WAVE` 削除・番号詰め直し
- 新規 `app/src/test/java/com/example/rakutencoverage/data/CollectionRecordTest.kt`
- 修正 `app/src/main/java/com/example/rakutencoverage/data/monster/BattleLogic.kt` — `battleType()` / `baseCatchRate()` から `MILLIMETER_WAVE` 分岐削除
- 新規 `app/src/test/java/com/example/rakutencoverage/data/monster/BattleLogicTest.kt`
- 修正 `app/src/main/java/com/example/rakutencoverage/data/monster/MonsterAssets.kt` — `moves: List<String>` を `movesByCategory: Map<MonsterCategory, List<String>>` に変更（6カテゴリ×10種）
- 新規 `app/src/test/java/com/example/rakutencoverage/data/monster/MonsterAssetsTest.kt`
- 修正 `app/src/main/java/com/example/rakutencoverage/data/monster/MonsterGenerator.kt` — カテゴリ別レンジ・カテゴリ別技プールを使うよう生成ロジック変更
- 新規 `app/src/test/java/com/example/rakutencoverage/data/monster/MonsterGeneratorTest.kt`
- 修正 `app/src/main/java/com/example/rakutencoverage/ui/collection/CollectionScreen.kt` — `displayName()`/`rarity()`/`starCount()`/`toEmoji()`/`toArgb()` を `MonsterCategory` 経由に書き換え

---

### Task 1: MonsterCategory enum新設

**Files:**
- Create: `app/src/main/java/com/example/rakutencoverage/data/monster/MonsterCategory.kt`
- Test: `app/src/test/java/com/example/rakutencoverage/data/monster/MonsterCategoryTest.kt`

**Interfaces:**
- Produces: `enum class MonsterCategory(val displayName: String, val rarityLabel: String, val starCount: Int, val emoji: String, val argbColor: Long, val hpRange: IntRange, val attackRange: IntRange, val defenseRange: IntRange)` の6値 `LEGEND, PHANTOM, ELITE, NORMAL, ROCKET, NORMAL_DARK`
- Produces: `val SignalLevel.category: MonsterCategory?`（`AIRPLANE_MODE`/`NO_SIM`は`null`）

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonsterCategoryTest {

    @Test
    fun platinumMapsToLegend() {
        assertEquals(MonsterCategory.LEGEND, SignalLevel.PLATINUM.category)
    }

    @Test
    fun platinum5gMapsToPhantom() {
        assertEquals(MonsterCategory.PHANTOM, SignalLevel.PLATINUM_5G.category)
    }

    @Test
    fun fiveGMapsToElite() {
        assertEquals(MonsterCategory.ELITE, SignalLevel.FIVE_G.category)
    }

    @Test
    fun lteMapsToNormal() {
        assertEquals(MonsterCategory.NORMAL, SignalLevel.LTE.category)
    }

    @Test
    fun weakMapsToRocket() {
        assertEquals(MonsterCategory.ROCKET, SignalLevel.WEAK.category)
    }

    @Test
    fun noSignalMapsToNormalDark() {
        assertEquals(MonsterCategory.NORMAL_DARK, SignalLevel.NO_SIGNAL.category)
    }

    @Test
    fun airplaneModeHasNoCategory() {
        assertNull(SignalLevel.AIRPLANE_MODE.category)
    }

    @Test
    fun noSimHasNoCategory() {
        assertNull(SignalLevel.NO_SIM.category)
    }

    @Test
    fun legendHasFullRanges() {
        assertEquals(70..100, MonsterCategory.LEGEND.hpRange)
        assertEquals(70..100, MonsterCategory.LEGEND.attackRange)
        assertEquals(70..100, MonsterCategory.LEGEND.defenseRange)
    }

    @Test
    fun phantomHasLowHpDefenseHighAttack() {
        assertEquals(30..60, MonsterCategory.PHANTOM.hpRange)
        assertEquals(70..100, MonsterCategory.PHANTOM.attackRange)
        assertEquals(30..60, MonsterCategory.PHANTOM.defenseRange)
    }

    @Test
    fun rocketHasLowDefense() {
        assertEquals(40..70, MonsterCategory.ROCKET.hpRange)
        assertEquals(70..100, MonsterCategory.ROCKET.attackRange)
        assertEquals(1..40, MonsterCategory.ROCKET.defenseRange)
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.example.rakutencoverage.data.monster.MonsterCategoryTest"`
Expected: FAIL（`MonsterCategory` unresolved reference）

- [ ] **Step 3: 実装を書く**

```kotlin
package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel

/**
 * モンスターの性格分類。signalLevel(電波品質)ごとに1:1で対応する。
 * ポケモンでいう伝説・幻・ロケット団のような世界観の軸で、
 * パラメータレンジ(HP/攻撃/防御)と技プール、図鑑表示に反映する。
 */
enum class MonsterCategory(
    val displayName: String,
    val rarityLabel: String,
    val starCount: Int,
    val emoji: String,
    val argbColor: Long,
    val hpRange: IntRange,
    val attackRange: IntRange,
    val defenseRange: IntRange
) {
    LEGEND(
        displayName = "伝説のプラチナモンスター",
        rarityLabel = "★★★★★ 伝説",
        starCount = 5,
        emoji = "👑",
        argbColor = 0xFFAB47BCL,
        hpRange = 70..100,
        attackRange = 70..100,
        defenseRange = 70..100
    ),
    PHANTOM(
        displayName = "幻のプラチナ5Gモンスター",
        rarityLabel = "★★★★☆ 幻",
        starCount = 4,
        emoji = "🤩",
        argbColor = 0xFFFFD700L,
        hpRange = 30..60,
        attackRange = 70..100,
        defenseRange = 30..60
    ),
    ELITE(
        displayName = "精鋭の5G Sub6モンスター",
        rarityLabel = "★★★☆☆ 精鋭",
        starCount = 3,
        emoji = "😆",
        argbColor = 0xFF1E88E5L,
        hpRange = 50..80,
        attackRange = 70..100,
        defenseRange = 40..70
    ),
    NORMAL(
        displayName = "LTEモンスター",
        rarityLabel = "★☆☆☆☆ コモン",
        starCount = 1,
        emoji = "🙂",
        argbColor = 0xFF43A047L,
        hpRange = 1..100,
        attackRange = 1..100,
        defenseRange = 1..100
    ),
    ROCKET(
        displayName = "ロケット団の鬼",
        rarityLabel = "★★☆☆☆ ロケット団",
        starCount = 2,
        emoji = "👹",
        argbColor = 0xFF5D4037L,
        hpRange = 40..70,
        attackRange = 70..100,
        defenseRange = 1..40
    ),
    NORMAL_DARK(
        displayName = "圏外の亡霊",
        rarityLabel = "★★★☆☆ 闇レア",
        starCount = 3,
        emoji = "💀",
        argbColor = 0xFF212121L,
        hpRange = 1..100,
        attackRange = 1..100,
        defenseRange = 1..100
    )
}

/** AIRPLANE_MODE/NO_SIMはコレクション対象外のためnull */
val SignalLevel.category: MonsterCategory? get() = when (this) {
    SignalLevel.PLATINUM                            -> MonsterCategory.LEGEND
    SignalLevel.PLATINUM_5G                         -> MonsterCategory.PHANTOM
    SignalLevel.FIVE_G                              -> MonsterCategory.ELITE
    SignalLevel.LTE                                 -> MonsterCategory.NORMAL
    SignalLevel.WEAK                                -> MonsterCategory.ROCKET
    SignalLevel.NO_SIGNAL                           -> MonsterCategory.NORMAL_DARK
    SignalLevel.AIRPLANE_MODE, SignalLevel.NO_SIM   -> null
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.example.rakutencoverage.data.monster.MonsterCategoryTest"`
Expected: PASS（11 tests）

- [ ] **Step 5: コミット**

```bash
git add app/src/main/java/com/example/rakutencoverage/data/monster/MonsterCategory.kt app/src/test/java/com/example/rakutencoverage/data/monster/MonsterCategoryTest.kt
git commit -m "feat: MonsterCategory enumとSignalLevel.category拡張を追加"
```

---

### Task 2: MILLIMETER_WAVE削除（SignalLevel / resolveSignalLevel）

**調査追記（実装時に判明）:** 当初の計画は `MILLIMETER_WAVE` 参照が `Measurement.kt` と後続タスクの4ファイル（`BattleLogic.kt`/`CollectionRecord.kt`/`CollectionScreen.kt`/`MonsterCategory.kt`）に限られる想定だったが、実装時のgrep調査で以下8ファイル＋テスト1件にも同じ列挙子への参照があることが判明した。`SignalLevel`から`MILLIMETER_WAVE`を削除すると、これらは全て「網羅的when式に存在しない列挙子」でコンパイルエラーになるため、Task 2のスコープに含めて一括修正する。

**Files:**
- Modify: `app/src/main/java/com/example/rakutencoverage/data/Measurement.kt:46-77`
- Modify: `app/src/main/java/com/example/rakutencoverage/data/monster/MonsterCategory.kt`（Task 1実装時にKotlinの網羅性チェックを満たすため暫定で追加された `SignalLevel.MILLIMETER_WAVE -> MonsterCategory.LEGEND` 分岐を、この Task で `SignalLevel` から `MILLIMETER_WAVE` を消すのに合わせて削除する）
- Modify: `app/src/main/java/com/example/rakutencoverage/ui/character/CharacterWidget.kt:21,36,87`
- Modify: `app/src/main/java/com/example/rakutencoverage/ui/character/CharacterState.kt:12-16,62`
- Modify: `app/src/main/java/com/example/rakutencoverage/ui/collection/CollectionViewModel.kt:56`
- Modify: `app/src/main/java/com/example/rakutencoverage/ui/map/MapViewModel.kt:83,99`
- Modify: `app/src/main/java/com/example/rakutencoverage/ui/map/MapScreen.kt:975,1006,1022,1281,1340,1484`
- Modify: `app/src/main/java/com/example/rakutencoverage/ui/history/HistoryScreen.kt:98,110`
- Modify: `app/src/main/java/com/example/rakutencoverage/widget/MeasurementWidget.kt:89,101`
- Modify: `app/src/main/java/com/example/rakutencoverage/measurement/NotificationDisplay.kt:19,34`
- Modify: `app/src/test/java/com/example/rakutencoverage/measurement/NotificationDisplayTest.kt:11`
- Test: `app/src/test/java/com/example/rakutencoverage/data/MeasurementTest.kt`

**Interfaces:**
- Consumes: なし（`SignalLevel` enumはこのタスクが基点）
- Produces: `SignalLevel` enumは8値（`MILLIMETER_WAVE`を除いた `PLATINUM_5G, FIVE_G, PLATINUM, LTE, WEAK, NO_SIGNAL, AIRPLANE_MODE, NO_SIM`）。`resolveSignalLevel(networkType: String, band: String?): SignalLevel` はn257入力時に`FIVE_G`を返す

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package com.example.rakutencoverage.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementTest {

    @Test
    fun n257FallsBackToFiveG() {
        assertEquals(SignalLevel.FIVE_G, resolveSignalLevel("5G", "n257"))
    }

    @Test
    fun n28Is5GPlatinum() {
        assertEquals(SignalLevel.PLATINUM_5G, resolveSignalLevel("5G", "n28"))
    }

    @Test
    fun band28Is5GPlatinum() {
        assertEquals(SignalLevel.PLATINUM_5G, resolveSignalLevel("5G", "Band 28"))
    }

    @Test
    fun otherFiveGBandIsFiveG() {
        assertEquals(SignalLevel.FIVE_G, resolveSignalLevel("5G", "n77"))
    }

    @Test
    fun lteBand28IsPlatinum() {
        assertEquals(SignalLevel.PLATINUM, resolveSignalLevel("LTE", "Band 28"))
    }

    @Test
    fun lteBand3IsLte() {
        assertEquals(SignalLevel.LTE, resolveSignalLevel("LTE", "Band 3"))
    }

    @Test
    fun lteBand18IsWeak() {
        assertEquals(SignalLevel.WEAK, resolveSignalLevel("LTE", "Band 18"))
    }

    @Test
    fun lteBand26IsWeak() {
        assertEquals(SignalLevel.WEAK, resolveSignalLevel("LTE", "Band 26"))
    }

    @Test
    fun noServiceIsNoSignal() {
        assertEquals(SignalLevel.NO_SIGNAL, resolveSignalLevel("NO_SERVICE", null))
    }

    @Test
    fun airplaneModeIsAirplaneMode() {
        assertEquals(SignalLevel.AIRPLANE_MODE, resolveSignalLevel("AIRPLANE_MODE", null))
    }

    @Test
    fun noSimIsNoSim() {
        assertEquals(SignalLevel.NO_SIM, resolveSignalLevel("NO_SIM", null))
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.example.rakutencoverage.data.MeasurementTest"`
Expected: FAIL（`n257FallsBackToFiveG` は現状 `MILLIMETER_WAVE` を返すため不一致）

- [ ] **Step 3: 実装を変更する**

`app/src/main/java/com/example/rakutencoverage/data/Measurement.kt` の34-77行目を以下に置き換える:

```kotlin
    /**
     * networkType と band の組み合わせから信号レベルを導出する。
     * 楽天プラチナバンド判定: LTE Band 28 / 5G n28。
     * auローミング判定: Band 18 / Band 26 を含む場合は WEAK 扱い。
     */
    val signalLevel: SignalLevel get() = resolveSignalLevel(networkType, band)

    /** チェックインモードかどうか (arenaId が設定されていれば true) */
    val isArenaMode: Boolean get() = arenaId != null
}

/** networkType と band から SignalLevel を導出する純粋関数。Measurement.signalLevel はこれに委譲する。 */
fun resolveSignalLevel(networkType: String, band: String?): SignalLevel = when (networkType) {
    "AIRPLANE_MODE"          -> SignalLevel.AIRPLANE_MODE
    "NO_SIM"                 -> SignalLevel.NO_SIM
    "NO_SERVICE", "UNKNOWN"  -> SignalLevel.NO_SIGNAL
    "5G" -> when {
        band == "Band 28" || band == "n28" -> SignalLevel.PLATINUM_5G
        else                               -> SignalLevel.FIVE_G
    }
    "LTE" -> when {
        band == "Band 28" || band == "n28"                           -> SignalLevel.PLATINUM
        band != null && !band.contains("18") && !band.contains("26") -> SignalLevel.LTE
        else                                                          -> SignalLevel.WEAK
    }
    else -> SignalLevel.WEAK
}

/**
 * 信号レベルの分類。強い順に並んでいる。
 * WEAK 以下は楽天回線として有効な通信ができていない状態。
 * MILLIMETER_WAVE(n257)は対応端末が存在せずエリアも極小のため削除済み(n257計測時はFIVE_G扱い)。
 */
enum class SignalLevel {
    PLATINUM_5G,     // 5G + プラチナバンド (n28)
    FIVE_G,          // 5G Sub6 (非プラチナ)
    PLATINUM,        // LTE Band 28 (プラチナバンド)
    LTE,             // LTE (楽天回線) ← コモン
    WEAK,            // 弱電界 / au パートナー回線 ← 闇
    NO_SIGNAL,       // 圏外 (SIM あり・機内モードなし) ← 闇
    AIRPLANE_MODE,   // 機内モード中 → スタンプ無効
    NO_SIM           // SIM 未挿入 → スタンプ無効
}
```

- [ ] **Step 3.5: MonsterCategory.kt の暫定分岐を削除する**

Task 1実装時、`SignalLevel`にまだ`MILLIMETER_WAVE`が残っていたため、Kotlinの網羅性チェックを満たす目的で
`SignalLevel.category`に`SignalLevel.MILLIMETER_WAVE -> MonsterCategory.LEGEND`という暫定分岐が追加されている。
`MILLIMETER_WAVE`をenumから削除するとこの行はコンパイルエラーになるため、削除する。

`app/src/main/java/com/example/rakutencoverage/data/monster/MonsterCategory.kt` を開き、
`val SignalLevel.category: MonsterCategory?` の中にある `SignalLevel.MILLIMETER_WAVE -> MonsterCategory.LEGEND` の行を削除する
（Step 3の変更後、`SignalLevel`はもう`MILLIMETER_WAVE`を持たないため、この行を残すとコンパイルエラーになる）。

- [ ] **Step 3.6: 残り8ファイル＋テスト1件からMILLIMETER_WAVE参照を削除する**

以下は全て「`SignalLevel`の網羅的when式」または「`SignalLevel`のリスト」から`MILLIMETER_WAVE`の行・要素を1行削除するだけの機械的な変更。他の分岐・要素の値は一切変更しない。

**`app/src/main/java/com/example/rakutencoverage/ui/character/CharacterWidget.kt`:**
- L21: `val isExcited = state.level == SignalLevel.MILLIMETER_WAVE || state.level == SignalLevel.PLATINUM_5G || state.level == SignalLevel.FIVE_G` を `val isExcited = state.level == SignalLevel.PLATINUM_5G || state.level == SignalLevel.FIVE_G` に変更
- L36: `SignalLevel.MILLIMETER_WAVE -> Color(0xFFFF6F00)` の行を削除
- L87: `SignalLevel.MILLIMETER_WAVE -> "👑 ミリ波5G (n257)"` の行を削除

**`app/src/main/java/com/example/rakutencoverage/ui/character/CharacterState.kt`:**
- L12-16: `messages`マップの以下のエントリ全体を削除:
  ```kotlin
      SignalLevel.MILLIMETER_WAVE to listOf(
          "ミリ波！！これは伝説級の電波だ！！！",
          "n257…！！幻のミリ波を捕まえた！！",
          "これがミリ波の力…！速すぎてこわい！！"
      ),
  ```
- L62: `SignalLevel.MILLIMETER_WAVE -> "👑"` の行を削除

**`app/src/main/java/com/example/rakutencoverage/ui/collection/CollectionViewModel.kt`:**
- L56: `lightLevels`リストの `SignalLevel.MILLIMETER_WAVE,` の行を削除

**`app/src/main/java/com/example/rakutencoverage/ui/map/MapViewModel.kt`:**
- L83: `val mmWave: Int    = 0,   // MILLIMETER_WAVE (n257)` の行を削除（`SignalCounts`データクラスのフィールド）
- L99: `mmWave = list.count { it.signalLevel == SignalLevel.MILLIMETER_WAVE },` の行を削除

**`app/src/main/java/com/example/rakutencoverage/ui/map/MapScreen.kt`:**
- L975: `badgeRingColor()`内の `SignalLevel.MILLIMETER_WAVE -> androidx.compose.ui.graphics.Color(0xFFFF6F00)` の行を削除
- L1006: `SignalCountPill()`内の `Text("👑${counts.mmWave}", color = GoWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)` の行を削除（残り2つの`Text`はそのまま）
- L1022: `SignalCountDetailSheet()`内の `Triple("5Gmm",  counts.mmWave,   androidx.compose.ui.graphics.Color(0xFFFF6F00)),` の行を削除
- L1281: `starCount()`内の `SignalLevel.MILLIMETER_WAVE -> 5` の行を削除
- L1340: `shortLabel()`内の `SignalLevel.MILLIMETER_WAVE -> "👑 ミリ波5G"` の行を削除
- L1484: `toColor()`内の `SignalLevel.MILLIMETER_WAVE -> Color.parseColor("#FF6F00")` の行を削除

**`app/src/main/java/com/example/rakutencoverage/ui/history/HistoryScreen.kt`:**
- L98: `toComposeColor()`内の `SignalLevel.MILLIMETER_WAVE -> Color(0xFFFF6F00)` の行を削除
- L110: `shortLabel()`内の `SignalLevel.MILLIMETER_WAVE -> "mmW"` の行を削除

**`app/src/main/java/com/example/rakutencoverage/widget/MeasurementWidget.kt`:**
- L89: `toEmoji()`内の `SignalLevel.MILLIMETER_WAVE -> "👑"` の行を削除
- L101: `shortLabel()`内の `SignalLevel.MILLIMETER_WAVE -> "ミリ波5G"` の行を削除

**`app/src/main/java/com/example/rakutencoverage/measurement/NotificationDisplay.kt`:**
- L19: `statusIconCategory()`内の `SignalLevel.MILLIMETER_WAVE,` の行を削除（`SignalLevel.PLATINUM_5G,` はそのまま残す）
- L34: `signalLevelDisplayName()`内の `SignalLevel.MILLIMETER_WAVE -> "ミリ波5G"` の行を削除

**`app/src/test/java/com/example/rakutencoverage/measurement/NotificationDisplayTest.kt`:**
- L11: `assertEquals(StatusIconCategory.PLATINUM, statusIconCategory(SignalLevel.MILLIMETER_WAVE))` というテストケース（1メソッド全体）を削除する。このテストは`MILLIMETER_WAVE`という値そのものをテストしているため、列挙子削除に伴いテストケースごと不要になる。削除前に該当メソッドの前後を読み、メソッド定義全体（`@Test fun ... { ... }`）を過不足なく消す。

- [ ] **Step 4: プロジェクト全体のテストが通ることを確認**

Step 3.6で`MILLIMETER_WAVE`参照は全ファイルから削除済みのため、フルテストスイートが通ることを確認する。

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL（`MeasurementTest`の11 tests含む全テストPASS。他タスク未着手のTask 3-6由来の分岐削除もれがあればここでコンパイルエラーとして検出される）

- [ ] **Step 5: コミット**

```bash
git add app/src/main/java/com/example/rakutencoverage/data/Measurement.kt \
  app/src/test/java/com/example/rakutencoverage/data/MeasurementTest.kt \
  app/src/main/java/com/example/rakutencoverage/data/monster/MonsterCategory.kt \
  app/src/main/java/com/example/rakutencoverage/ui/character/CharacterWidget.kt \
  app/src/main/java/com/example/rakutencoverage/ui/character/CharacterState.kt \
  app/src/main/java/com/example/rakutencoverage/ui/collection/CollectionViewModel.kt \
  app/src/main/java/com/example/rakutencoverage/ui/map/MapViewModel.kt \
  app/src/main/java/com/example/rakutencoverage/ui/map/MapScreen.kt \
  app/src/main/java/com/example/rakutencoverage/ui/history/HistoryScreen.kt \
  app/src/main/java/com/example/rakutencoverage/widget/MeasurementWidget.kt \
  app/src/main/java/com/example/rakutencoverage/measurement/NotificationDisplay.kt \
  app/src/test/java/com/example/rakutencoverage/measurement/NotificationDisplayTest.kt
git commit -m "feat: SignalLevelからMILLIMETER_WAVEを削除(n257はFIVE_G扱いにフォールバック)"
```

---

### Task 3: CollectionRecord.rarityRank / BattleLogicの追従修正

**Files:**
- Modify: `app/src/main/java/com/example/rakutencoverage/data/CollectionRecord.kt:24-35`
- Modify: `app/src/main/java/com/example/rakutencoverage/data/monster/BattleLogic.kt:39-45, 106-114`
- Test: `app/src/test/java/com/example/rakutencoverage/data/CollectionRecordTest.kt`
- Test: `app/src/test/java/com/example/rakutencoverage/data/monster/BattleLogicTest.kt`

**Interfaces:**
- Consumes: Task 2で確定した8値の `SignalLevel`
- Produces: `SignalLevel.rarityRank: Int`（0=最もレア、`PLATINUM_5G`が0に繰り上がる）、`typeMultiplier()`/`baseCatchRate()` はMILLIMETER_WAVE分岐なしで動作

- [ ] **Step 1: 失敗するテストを書く**

`CollectionRecordTest.kt`:

```kotlin
package com.example.rakutencoverage.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionRecordTest {

    @Test
    fun platinum5gIsRarest() {
        assertEquals(0, SignalLevel.PLATINUM_5G.rarityRank)
    }

    @Test
    fun noSignalIsLeastRare() {
        assertEquals(5, SignalLevel.NO_SIGNAL.rarityRank)
    }

    @Test
    fun rarityRankIsStrictlyOrdered() {
        val ordered = listOf(
            SignalLevel.PLATINUM_5G,
            SignalLevel.FIVE_G,
            SignalLevel.PLATINUM,
            SignalLevel.LTE,
            SignalLevel.WEAK,
            SignalLevel.NO_SIGNAL
        )
        val ranks = ordered.map { it.rarityRank }
        assertEquals(ranks.sorted(), ranks)
        assertEquals(ranks.distinct(), ranks)
    }

    @Test
    fun airplaneModeAndNoSimAreNotCollectable() {
        assertEquals(99, SignalLevel.AIRPLANE_MODE.rarityRank)
        assertEquals(99, SignalLevel.NO_SIM.rarityRank)
        assert(!SignalLevel.AIRPLANE_MODE.isCollectable)
        assert(!SignalLevel.NO_SIM.isCollectable)
    }
}
```

`BattleLogicTest.kt`:

```kotlin
package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class BattleLogicTest {

    @Test
    fun lteBeatsPlatinum() {
        assertEquals(1.5, typeMultiplier(SignalLevel.LTE, SignalLevel.PLATINUM), 0.001)
    }

    @Test
    fun platinumBeatsFiveG() {
        assertEquals(1.5, typeMultiplier(SignalLevel.PLATINUM, SignalLevel.FIVE_G), 0.001)
    }

    @Test
    fun weakIsGlassCannonAgainstAnything() {
        assertEquals(1.25, typeMultiplier(SignalLevel.WEAK, SignalLevel.LTE), 0.001)
        assertEquals(1.25, typeMultiplier(SignalLevel.LTE, SignalLevel.WEAK), 0.001)
    }

    @Test
    fun baseCatchRateHasNoMillimeterWaveBranch() {
        assertEquals(0.28, baseCatchRate(SignalLevel.PLATINUM_5G), 0.001)
        assertEquals(0.40, baseCatchRate(SignalLevel.FIVE_G), 0.001)
        assertEquals(0.40, baseCatchRate(SignalLevel.PLATINUM), 0.001)
        assertEquals(0.40, baseCatchRate(SignalLevel.NO_SIGNAL), 0.001)
        assertEquals(0.55, baseCatchRate(SignalLevel.WEAK), 0.001)
        assertEquals(0.75, baseCatchRate(SignalLevel.LTE), 0.001)
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.example.rakutencoverage.data.CollectionRecordTest" --tests "com.example.rakutencoverage.data.monster.BattleLogicTest"`
Expected: FAIL（`SignalLevel.MILLIMETER_WAVE` を参照しているため現行コードはコンパイルエラー）

- [ ] **Step 3: 実装を変更する**

`CollectionRecord.kt` の24-35行目を置き換え:

```kotlin
/** SignalLevel のレア度順序（小さいほど強い・レア）。AIRPLANE_MODE/NO_SIMはコレクション対象外 */
val SignalLevel.rarityRank: Int get() = when (this) {
    SignalLevel.PLATINUM_5G     -> 0
    SignalLevel.FIVE_G          -> 1
    SignalLevel.PLATINUM        -> 2
    SignalLevel.LTE             -> 3
    SignalLevel.WEAK            -> 4
    SignalLevel.NO_SIGNAL       -> 5
    SignalLevel.AIRPLANE_MODE   -> 99
    SignalLevel.NO_SIM          -> 99
}
```

`BattleLogic.kt` の39-45行目を置き換え:

```kotlin
private fun SignalLevel.battleType(): Int = when (this) {
    SignalLevel.LTE                                   -> 0
    SignalLevel.PLATINUM, SignalLevel.PLATINUM_5G     -> 1
    SignalLevel.FIVE_G                                -> 2
    else                                               -> -1  // 闇
}
```

`BattleLogic.kt` の106-114行目を置き換え:

```kotlin
/** 捕獲基本確率(レア度が高いほど捕まえにくい)。denpamon-go 移植 */
fun baseCatchRate(level: SignalLevel): Double = when (level) {
    SignalLevel.PLATINUM_5G     -> 0.28
    SignalLevel.FIVE_G,
    SignalLevel.PLATINUM,
    SignalLevel.NO_SIGNAL       -> 0.40
    SignalLevel.WEAK            -> 0.55
    else                        -> 0.75  // LTE ほか
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.example.rakutencoverage.data.CollectionRecordTest" --tests "com.example.rakutencoverage.data.monster.BattleLogicTest"`
Expected: PASS（4 + 4 tests）

- [ ] **Step 5: コミット**

```bash
git add app/src/main/java/com/example/rakutencoverage/data/CollectionRecord.kt app/src/main/java/com/example/rakutencoverage/data/monster/BattleLogic.kt app/src/test/java/com/example/rakutencoverage/data/CollectionRecordTest.kt app/src/test/java/com/example/rakutencoverage/data/monster/BattleLogicTest.kt
git commit -m "fix: CollectionRecord/BattleLogicのMILLIMETER_WAVE参照を削除"
```

---

### Task 4: MonsterAssets.kt 技リストのカテゴリ別マップ化

**Files:**
- Modify: `app/src/main/java/com/example/rakutencoverage/data/monster/MonsterAssets.kt:31-37`
- Test: `app/src/test/java/com/example/rakutencoverage/data/monster/MonsterAssetsTest.kt`

**Interfaces:**
- Consumes: `MonsterCategory`（Task 1）
- Produces: `MonsterAssets.movesByCategory: Map<MonsterCategory, List<String>>`（各カテゴリ10種、既存の `MonsterAssets.moves` は削除）

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package com.example.rakutencoverage.data.monster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterAssetsTest {

    @Test
    fun everyCategoryHasTenMoves() {
        MonsterCategory.entries.forEach { category ->
            assertEquals(
                "category=$category",
                10,
                MonsterAssets.movesByCategory[category]?.size
            )
        }
    }

    @Test
    fun noDuplicateMovesAcrossCategories() {
        val allMoves = MonsterAssets.movesByCategory.values.flatten()
        assertEquals(allMoves.size, allMoves.toSet().size)
    }

    @Test
    fun millimeterWaveBeamBelongsToPhantom() {
        assertTrue("ミリ波ビーム" in MonsterAssets.movesByCategory[MonsterCategory.PHANTOM]!!)
    }

    @Test
    fun roamingKickBelongsToRocket() {
        assertTrue("ローミングキック" in MonsterAssets.movesByCategory[MonsterCategory.ROCKET]!!)
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.example.rakutencoverage.data.monster.MonsterAssetsTest"`
Expected: FAIL（`movesByCategory` unresolved reference）

- [ ] **Step 3: 実装を変更する**

`MonsterAssets.kt` の31-37行目（`val moves = listOf(...)`）を以下に置き換える:

```kotlin
    val movesByCategory: Map<MonsterCategory, List<String>> = mapOf(
        MonsterCategory.LEGEND to listOf(
            "プラチナアタック", "周波数の嵐", "スペクトル斬り", "シグナルフラッシュ",
            "キングオブバンド", "プラチナシールド", "ロイヤルウェーブ", "グランドカバレッジ",
            "サブロクキング", "ソブリンシグナル"
        ),
        MonsterCategory.PHANTOM to listOf(
            "ミリ波ビーム", "ビームフォーミング", "ゼロレイテンシ", "ファントムウェーブ",
            "ミラージュセル", "シークレットバンド", "ゴーストシグナル", "レアバンドフラッシュ",
            "ヴェールオブエヌニハチ", "イリュージョンピング"
        ),
        MonsterCategory.ELITE to listOf(
            "5Gバースト", "キャリアアグリ", "ハンドオーバー", "サブシックスダッシュ",
            "クイックコネクト", "エリートスラッシュ", "ハイスピードジャブ", "ネットワークソード",
            "ミリ秒キック", "アグレッシブビーム"
        ),
        MonsterCategory.NORMAL to listOf(
            "バンドチェンジ", "アンテナ乱舞", "電磁波バリア", "マクロシールド",
            "スタンダードパンチ", "ベーシックウェーブ", "デイリーコネクト", "ノーマルシグナル",
            "フォースリーパンチ", "テッパンリンク"
        ),
        MonsterCategory.ROCKET to listOf(
            "電波ジャック", "ローミングキック", "バックホール切断", "ジャミングパンチ",
            "パートナーキック", "なりすましビーム", "カンショウハビーム", "ローミングスティール",
            "ネットワークスラム", "ブンドリアンテナ"
        ),
        MonsterCategory.NORMAL_DARK to listOf(
            "ピング爆弾", "セルタワー落とし", "パケット乱射", "オニノパケット",
            "ゼロバーズパンチ", "ケンガイラッシュ", "ダークシグナル", "ロストコネクション",
            "サーチングフューリー", "ノーバーズキック"
        )
    )
```

（ファイル冒頭の `import` に `MonsterCategory` は同一パッケージのため追加不要）

- [ ] **Step 4: テストが通ることを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.example.rakutencoverage.data.monster.MonsterAssetsTest"`
Expected: PASS（4 tests）。この時点で `MonsterGenerator.kt` が旧 `MonsterAssets.moves` を参照しているためプロジェクト全体のコンパイルは失敗する（Task 5で解消）。

- [ ] **Step 5: コミット**

```bash
git add app/src/main/java/com/example/rakutencoverage/data/monster/MonsterAssets.kt app/src/test/java/com/example/rakutencoverage/data/monster/MonsterAssetsTest.kt
git commit -m "feat: 技リストをカテゴリ別プール(6x10種)に再構成"
```

---

### Task 5: MonsterGenerator.kt のカテゴリ別レンジ・技プール反映

**Files:**
- Modify: `app/src/main/java/com/example/rakutencoverage/data/monster/MonsterGenerator.kt:19-34`
- Test: `app/src/test/java/com/example/rakutencoverage/data/monster/MonsterGeneratorTest.kt`

**Interfaces:**
- Consumes: `SignalLevel.category`（Task 1）、`MonsterCategory.hpRange/attackRange/defenseRange`（Task 1）、`MonsterAssets.movesByCategory`（Task 4）
- Produces: `MonsterGenerator.generate(cellId: String, encounterCount: Int, signalLevel: SignalLevel): Monster` は変更なし（既存呼び出し元 `CollectionViewModel.kt`, `MapViewModel.kt`, `MeasurementService.kt`, `MonsterRepository.kt` はシグネチャ変更不要）

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.SignalLevel
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterGeneratorTest {

    private val testCellIds = (1..30).map { "test-cell-$it" }

    @Test
    fun legendStatsStayWithinRange() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.PLATINUM)
            assertTrue("hp=${monster.hp}", monster.hp in MonsterCategory.LEGEND.hpRange)
            assertTrue("attack=${monster.attack}", monster.attack in MonsterCategory.LEGEND.attackRange)
            assertTrue("defense=${monster.defense}", monster.defense in MonsterCategory.LEGEND.defenseRange)
        }
    }

    @Test
    fun phantomStatsStayWithinRange() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.PLATINUM_5G)
            assertTrue(monster.hp in MonsterCategory.PHANTOM.hpRange)
            assertTrue(monster.attack in MonsterCategory.PHANTOM.attackRange)
            assertTrue(monster.defense in MonsterCategory.PHANTOM.defenseRange)
        }
    }

    @Test
    fun rocketStatsStayWithinRange() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.WEAK)
            assertTrue(monster.hp in MonsterCategory.ROCKET.hpRange)
            assertTrue(monster.attack in MonsterCategory.ROCKET.attackRange)
            assertTrue(monster.defense in MonsterCategory.ROCKET.defenseRange)
        }
    }

    @Test
    fun normalStatsStayWithinFullRange() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.LTE)
            assertTrue(monster.hp in 1..100)
            assertTrue(monster.attack in 1..100)
            assertTrue(monster.defense in 1..100)
        }
    }

    @Test
    fun movesComeFromMatchingCategoryPool() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.PLATINUM)
            monster.moves.forEach { move ->
                assertTrue(
                    "move=$move not in LEGEND pool",
                    move in MonsterAssets.movesByCategory[MonsterCategory.LEGEND]!!
                )
            }
        }
    }

    @Test
    fun rocketMovesComeFromRocketPool() {
        testCellIds.forEach { cellId ->
            val monster = MonsterGenerator.generate(cellId, 1, SignalLevel.WEAK)
            monster.moves.forEach { move ->
                assertTrue(
                    "move=$move not in ROCKET pool",
                    move in MonsterAssets.movesByCategory[MonsterCategory.ROCKET]!!
                )
            }
        }
    }

    @Test
    fun sameCellIdIsDeterministic() {
        val a = MonsterGenerator.generate("fixed-cell", 1, SignalLevel.PLATINUM)
        val b = MonsterGenerator.generate("fixed-cell", 1, SignalLevel.PLATINUM)
        assertTrue(a.hp == b.hp && a.attack == b.attack && a.defense == b.defense && a.moves == b.moves)
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.example.rakutencoverage.data.monster.MonsterGeneratorTest"`
Expected: FAIL（現行実装はレンジ制約なしの1-100均一乱数、`MonsterAssets.moves` 参照でコンパイルエラー）

- [ ] **Step 3: 実装を変更する**

`MonsterGenerator.kt` の `generate()` 内、19-34行目を以下に置き換える:

```kotlin
        // AIRPLANE_MODE/NO_SIMはモンスター生成対象外(isCollectable=false)の前提。
        // 万一到達した場合はNORMAL相当のフルレンジにフォールバックする。
        val category = signalLevel.category ?: MonsterCategory.NORMAL

        val hp      = rng.nextInt(category.hpRange.last - category.hpRange.first + 1) + category.hpRange.first
        val attack  = rng.nextInt(category.attackRange.last - category.attackRange.first + 1) + category.attackRange.first
        val defense = rng.nextInt(category.defenseRange.last - category.defenseRange.first + 1) + category.defenseRange.first

        val movePool = MonsterAssets.movesByCategory.getValue(category)
        val moveCount = rng.nextInt(3) // 0, 1, 2
        val moves = if (moveCount == 0) {
            emptyList()
        } else {
            val shuffled = movePool.toMutableList()
            // Fisher-Yates で先頭 moveCount 個を選ぶ
            for (i in 0 until moveCount) {
                val j = i + rng.nextInt(shuffled.size - i)
                val tmp = shuffled[i]; shuffled[i] = shuffled[j]; shuffled[j] = tmp
            }
            shuffled.take(moveCount)
        }
```

（`import com.example.rakutencoverage.data.SignalLevel` は既存のまま。`category` プロパティは同一パッケージの拡張関数なので追加importは不要）

- [ ] **Step 4: テストが通ることを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.example.rakutencoverage.data.monster.MonsterGeneratorTest"`
Expected: PASS（7 tests）

- [ ] **Step 5: プロジェクト全体のユニットテストを実行し、これまでの変更が壊れていないことを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL（全テストPASS。まだ`CollectionScreen.kt`はコンパイルエラーの可能性があるため、Task 6完了までは失敗しうる。その場合はTask 6完了後に再実行して確認する）

- [ ] **Step 6: コミット**

```bash
git add app/src/main/java/com/example/rakutencoverage/data/monster/MonsterGenerator.kt app/src/test/java/com/example/rakutencoverage/data/monster/MonsterGeneratorTest.kt
git commit -m "feat: モンスター生成をカテゴリ別レンジ・技プールに対応"
```

---

### Task 6: CollectionScreen.kt の表示ロジックをMonsterCategory経由に統一

**Files:**
- Modify: `app/src/main/java/com/example/rakutencoverage/ui/collection/CollectionScreen.kt:275-390`

**Interfaces:**
- Consumes: `SignalLevel.category`（Task 1）、`MonsterCategory.displayName/rarityLabel/starCount/emoji/argbColor`（Task 1）
- Produces: 変更なし（`private fun` のシグネチャ・呼び出し側は同じ。中身の実装だけカテゴリ経由に差し替え）

これはCompose UI（`@Composable`関数から呼ばれるprivate拡張関数）のため、JVMユニットテストでの検証は行わず、目視確認とビルド成功で確認する。

- [ ] **Step 1: 336-390行目の5つの拡張関数を書き換える**

`CollectionScreen.kt` の336-390行目を以下に置き換える:

```kotlin
private fun SignalLevel.displayName(): String =
    category?.displayName ?: when (this) {
        SignalLevel.AIRPLANE_MODE -> "機内モードの幽霊"
        SignalLevel.NO_SIM        -> "SIMなしの亡者"
        else                      -> "" // category は AIRPLANE_MODE/NO_SIM 以外は非null
    }

private fun SignalLevel.rarity(): String = category?.rarityLabel ?: ""

private fun SignalLevel.starCount(): Int = category?.starCount ?: 1

private fun SignalLevel.toEmoji(): String = category?.emoji ?: "？"

private fun SignalLevel.toArgb(): Long = category?.argbColor ?: 0xFF9E9E9EL
```

- [ ] **Step 2: importを確認**

ファイル冒頭に `import com.example.rakutencoverage.data.monster.category` が無ければ追加する（`category` は `data.monster.MonsterCategory.kt` で定義した拡張プロパティ）。

```bash
grep -n "^import com.example.rakutencoverage.data.monster" /Users/osamu.kawakami/claude-practice/platinum-hunter/app/src/main/java/com/example/rakutencoverage/ui/collection/CollectionScreen.kt
```

無ければ既存のimport群の末尾に追加する:

```kotlin
import com.example.rakutencoverage.data.monster.category
```

- [ ] **Step 3: プロジェクト全体のビルドが通ることを確認**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: プロジェクト全体のユニットテストを実行**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL（全タスクのテストが揃ってPASSすること）

- [ ] **Step 5: 実機/エミュレータで図鑑画面を目視確認**（可能であれば）

図鑑画面（Collection画面）を開き、各カテゴリのグループヘッダーに新しい表示名（「伝説のプラチナモンスター」「ロケット団の鬼」等）と星数が正しく出ていることを確認する。実機がない場合はこのステップをスキップし、その旨をユーザーに報告する。

- [ ] **Step 6: コミット**

```bash
git add app/src/main/java/com/example/rakutencoverage/ui/collection/CollectionScreen.kt
git commit -m "refactor: CollectionScreenの表示ロジックをMonsterCategory経由に統一"
```

---

## 完了条件

- [ ] `./gradlew testDebugUnitTest` が全件PASS
- [ ] `./gradlew assembleDebug` がBUILD SUCCESSFUL
- [ ] 図鑑画面で `MILLIMETER_WAVE`（5Gミリ波モンスター）のカテゴリが表示されないこと
- [ ] 図鑑画面でLTE Band28（プラチナ）捕獲時に「伝説のプラチナモンスター」表示になること（実機確認できる場合）
