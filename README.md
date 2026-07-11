# Rakuten Coverage

楽天モバイルの電波強度を計測・記録して遊べる Android アプリ(個人開発)。
移動しながら電波を実測し、基地局ごとに決定論生成されるモンスターを捕まえてコレクションする、ポケモンGO風のUIを採用しています。

## 主な機能

- **電波計測**: GPS + TelephonyManager でバンド・電界強度・ping を定期計測し Room に記録
- **マップ表示**: OSMDroid 上に計測結果をカテゴリ色のドットで表示(ズーム連動サイズ)
- **モンスター図鑑**: 基地局ID(LTE: ci / NR: nci)をシードに同じセルから常に同じモンスターを生成。手動捕獲・オートキャプチャ対応
- **スタンプラリー**: 道の駅・Bリーグアリーナの GeoJSON スポットでチェックイン
- **データエクスポート**: 計測データの CSV 出力

## 技術スタック

Kotlin / Jetpack Compose (Material3) / Room / OSMDroid / FusedLocationProviderClient / KSP

## ビルド

Android Studio でこのフォルダを開いてビルドしてください。`local.properties` に SDK パスが必要です。

リリースビルド(署名)と Google Play 公開の手順は [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) を、
プライバシーポリシーは [PRIVACY_POLICY.md](PRIVACY_POLICY.md) を参照。

開発メモは [HANDOFF.md](HANDOFF.md) と [REDESIGN_PLAN.md](REDESIGN_PLAN.md) を参照。

## 免責事項

- 本アプリは個人が開発した**非公式**アプリであり、楽天モバイル株式会社およびその関連会社とは一切関係ありません。
- 「楽天モバイル」その他の商標は各権利者に帰属します。
- 電波測定結果は端末環境に依存する参考値であり、正確性を保証するものではありません。
- 道の駅データ: 出典 国土交通省 国土数値情報(道の駅データ P35-18)を加工して作成(2018年度時点のデータ)。
- Bリーグアリーナ情報は Wikipedia・各クラブ公式サイト等の公開情報を基に作成しており、シーズンにより変更される場合があります。
- イオンモールデータ: イオン公式サイト店舗一覧(業態:イオンモール)を基に、座標は OpenStreetMap(© OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright))の Overpass API・Nominatim から取得して作成(2026年7月時点)。店舗の改廃により実際と異なる場合があります。

## ライセンス

[MIT License](LICENSE)
