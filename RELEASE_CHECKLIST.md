# Google Play 公開チェックリスト

「最強プラチナハンター」を Google Play で公開するための手順と注意点。
コード側で対応済みの項目と、Play Console 等で人手が必要な手続きを分けて整理する。

---

## 1. コード側の対応状況(このリポジトリで対応済み)

- [x] **applicationId の変更**: `com.example.rakutencoverage` → `io.github.tarepon.platinumhunter`
      (Google Play は `com.example.*` を受け付けない。**初回アップロード後は二度と変更できない**ので、
      別のIDにしたい場合は公開前に `app/build.gradle.kts` の1行を変更する。
      `io.github.<GitHubユーザー名>.*` は所有ドメイン不要で使える慣例的な形式)
- [x] **versionName**: `1.0.0`(`versionCode = 1`)
- [x] **リリース署名設定**: `keystore.properties` があれば自動で署名(下記 §2)
- [x] targetSdk 35(2025年8月以降の新規アプリ要件を満たす)
- [x] R8 によるコード縮小・リソース縮小(`minifyEnabled` / `shrinkResources`)
- [x] 計測DBをクラウドバックアップ・端末間転送から除外(`backup_rules.xml` / `data_extraction_rules.xml`)
- [x] OpenStreetMap の帰属表示(`CopyrightOverlay`)と User-Agent 設定
- [x] 権限は最小限(ACCESS_BACKGROUND_LOCATION は未使用。バックグラウンド計測は FGS(type=location)+ACCESS_FINE_LOCATION で実現)
- [x] プライバシーポリシー(`PRIVACY_POLICY.md`)
- [x] デバッグログなし・広告/解析SDKなし
- [ ] **次回 Play 提出時**: バックグラウンド計測(フォアグラウンドサービス)を追加したため、
      Play Console の「アプリのコンテンツ」→ 権限と API の宣言 で
      フォアグラウンドサービス(location)の利用目的の申告が必要(初回リリース後の追加のため要対応)

## 2. 署名鍵の作成(初回のみ・ローカルで実施)

```bash
# リポジトリ直下で実行(release.jks は .gitignore 済み)
keytool -genkeypair -v \
  -keystore release.jks -alias release \
  -keyalg RSA -keysize 2048 -validity 10000

# テンプレートをコピーしてパスワード等を記入
cp keystore.properties.example keystore.properties
```

- **release.jks と keystore.properties は絶対にコミットしない**(.gitignore 済み)。
- 鍵を紛失するとアプリ更新ができなくなるため、パスワードマネージャー等に安全にバックアップする。
- Play Console では **Play App Signing**(Google が署名鍵を管理)を選ぶのを推奨。
  その場合 release.jks は「アップロード鍵」となり、紛失してもリセット申請が可能になる。

## 3. リリースビルド(ローカルの Android Studio / SDK 環境で実施)

```bash
./gradlew :app:bundleRelease   # Play 提出用 AAB → app/build/outputs/bundle/release/
./gradlew :app:assembleRelease # 実機テスト用 APK → app/build/outputs/apk/release/
```

ビルド後、実機に release APK を入れて最低限の動作確認をする:

- [ ] 地図表示・タイル読み込み
- [ ] 位置情報権限ダイアログ → 計測開始 → ドット表示
- [ ] モンスター発見・捕獲 → 図鑑反映(R8 で Room 周りが壊れていないかの確認)
- [ ] CSV エクスポート / JSON バックアップの共有
- [ ] ウィジェット追加と「起動して計測」

## 4. プライバシーポリシーの公開(必須)

位置情報権限を使うアプリはストア掲載時にポリシー URL が必須。

- 最も簡単: GitHub リポジトリを public にして `PRIVACY_POLICY.md` の GitHub ページ URL を使う。
  リポジトリを private のままにしたい場合は GitHub Pages や Google Sites 等で同内容を公開する。
- [ ] 公開 URL を Play Console の「アプリのコンテンツ → プライバシーポリシー」に設定

## 5. Play Console の手続き

1. [ ] **デベロッパー アカウント登録**(個人: $25 買い切り、本人確認あり・数日かかることがある)
2. [ ] **個人アカウントの新規アプリはクローズドテストが必須**:
       製品版公開の前に **12人以上のテスターで14日間**のクローズドテストを実施する必要がある
       (2023年11月以降に作成された個人アカウントが対象)。テスター集めを見込んでスケジュールする。
3. [ ] アプリ作成(名前: 最強プラチナハンター / 日本語 / 無料)
4. [ ] **ストア掲載情報**:
   - 512×512 アイコン、1024×500 フィーチャーグラフィック、スクリーンショット(スマホ縦 最低2枚)
   - 簡単な説明(80字)・詳しい説明(4000字)
   - **説明文に「非公式アプリであり楽天モバイル株式会社とは無関係」である旨を必ず明記**(§7)
5. [ ] **データセーフティフォーム**: 本アプリの実装での回答目安
   - 計測した緯度経度は端末内保存のみ。ただし地図タイル取得時に IP アドレスと表示範囲の
     タイル座標が OSM サーバーへ送られるため、データセーフティ上の扱いを公開時点の設問で確認する。
     保守的には「おおよその位置 / アプリの機能 / 第三者との共有」を申告する
   - 利用者がスピードテストを実行した場合、Cloudflare との通信で IP アドレスが扱われる
   - 広告・解析・トラッキング: なし
   - データ暗号化(転送時): すべて HTTPS → はい / データ削除リクエスト: アンインストールで全削除
6. [ ] **コンテンツ レーティング質問票**(暴力・ギャンブル等すべて「なし」→ 全年齢相当)
7. [ ] 広告の有無: 「広告なし」を申告
8. [ ] 対象年齢・ニュース アプリ等の申告(該当なし)
9. [ ] クローズドテスト → 製品版への昇格申請 → 審査(数日)

※ `ACCESS_BACKGROUND_LOCATION` は要求していないが、アプリを閉じた後も location FGS で計測を
   続ける機能は Play の審査上「バックグラウンドでの位置情報」に相当すると判断される可能性がある。
   Play Console に表示される位置情報の申告を省略せず、アプリ内開示・ストア説明・短い実演動画を
   同じ内容で用意する。

## 6. OpenStreetMap タイルの利用ポリシー(公開前に把握しておく)

現在は OSM 公式タイルサーバー(`tile.openstreetmap.org` / osmdroid の `MAPNIK`)を使用している。
[OSMF Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/) では、
**広く配布されるアプリが公式タイルサーバーを既定の地図として使うことは推奨されない**(重負荷時は遮断されうる)。

- 小規模配布(クローズドテスト〜数百人)のうちは実害はまず出ない
- ユーザーが増えてきたら MapTiler / Thunderforest / Stadia Maps 等の無料枠付きタイルプロバイダへの
  移行を検討する(`MapScreen.kt` の `setTileSource` を差し替え + APIキー)
- 帰属表示(© OpenStreetMap contributors)は `CopyrightOverlay` で対応済み。タイル提供元を変えた場合も帰属表示は必須

## 7. 商標・非公式アプリとしての注意

- アプリ名・アイコンに「楽天」「Rakuten」やそのロゴを**使わない**(現状の「最強プラチナハンター」は問題なし)
- ストア説明文・スクリーンショット内でも公式アプリと誤認させる表現を避け、
  README と同様の非公式である旨の免責を説明文に記載する
- Play の「なりすまし」ポリシー違反はアカウント停止リスクがあるため最優先で守る

## 8. 公開後の運用

- 更新のたびに `versionCode` を +1、`versionName` を適宜更新(`app/build.gradle.kts`)
- クラッシュ監視は Play Console の「Android vitals」で無料で確認可能(SDK 追加不要)
- OSM タイル負荷・レビューでの電池消費指摘などをウォッチ
