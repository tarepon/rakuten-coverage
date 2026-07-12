#!/bin/bash
# 未署名リリースAPK(Claudeが自動ビルド)への署名+実機インストールをまとめて行う。
# 署名パスワードはapksignerの対話プロンプトで直接入力する(このスクリプト・Claudeには渡らない)。
#
# 使い方:
#   1. Claudeにビルドしてもらう(./gradlew :app:assembleRelease または通常のセッション内ビルド)
#   2. ターミナルで ./scripts/sign_and_install.sh を実行
#   3. keystoreパスワード・キーパスワードを聞かれたら入力
set -euo pipefail
cd "$(dirname "$0")/.."

APK_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
APK_SIGNED="app/build/outputs/apk/release/app-release-signed.apk"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"

if [ ! -f "$APK_UNSIGNED" ]; then
  echo "未署名APKが見つかりません。先に ./gradlew :app:assembleRelease を実行してください。"
  exit 1
fi

if [ ! -f release.jks ]; then
  echo "release.jks が見つかりません。リポジトリ直下に配置してください。"
  exit 1
fi

BUILD_TOOLS_DIR=$(ls -d "$HOME"/Library/Android/sdk/build-tools/*/ | sort -V | tail -1)
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

echo "署名中... (keystoreパスワード・キーパスワードの入力を求められます)"
"${BUILD_TOOLS_DIR}apksigner" sign --ks release.jks --ks-key-alias release --out "$APK_SIGNED" "$APK_UNSIGNED"

echo "署名OK。実機へインストールします..."
"$ADB" install -r "$APK_SIGNED"

echo "✅ 完了しました。"
