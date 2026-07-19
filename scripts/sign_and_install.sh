#!/bin/bash
# ビルド→署名→実機インストール→検証 を一気通貫で行う。
#
# 署名パスワードは macOS Keychain に登録しておけば自動取得する(平文ファイル不使用)。
# 未登録なら従来どおり apksigner の対話プロンプトで入力する。
#
# Keychainへの登録(初回のみ。-w でパスワードは隠し入力され、履歴に残らない):
#   security add-generic-password -a "$USER" -s platinum-hunter-ks -w    # keystoreパスワード
#   security add-generic-password -a "$USER" -s platinum-hunter-key -w   # キーパスワード(storeと同じなら省略可)
# 解除したいとき:
#   security delete-generic-password -s platinum-hunter-ks
#
# 使い方:
#   ./scripts/sign_and_install.sh              # ビルドから実行 (推奨)
#   ./scripts/sign_and_install.sh --no-build   # ビルド済みAPKに署名+インストールのみ
#
# 旧版からの改善 (2026-07-19 の「古いAPKを署名してインストールしていた」事故の再発防止):
#   1. 既定でビルドから実行し、ビルド漏れによる古い未署名APKの署名を防ぐ
#   2. インストール後、端末上のAPKサイズがいま署名したAPKと一致するか検証する
set -euo pipefail
cd "$(dirname "$0")/.."

APK_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
APK_SIGNED="app/build/outputs/apk/release/app-release-signed.apk"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
PKG="io.github.tarepon.platinumhunter"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

if [ "${1:-}" != "--no-build" ]; then
  echo "▶ ビルド中... ($(git log --oneline -1 | head -c 60))"
  ./gradlew :app:assembleRelease
fi

if [ ! -f "$APK_UNSIGNED" ]; then
  echo "❌ 未署名APKが見つかりません。先に ./gradlew :app:assembleRelease を実行してください。"
  exit 1
fi

if [ ! -f release.jks ]; then
  echo "❌ release.jks が見つかりません。リポジトリ直下に配置してください。"
  exit 1
fi

BUILD_TOOLS_DIR=$(ls -d "$HOME"/Library/Android/sdk/build-tools/*/ | sort -V | tail -1)

# Keychainからパスワードを取得 (未登録なら空)。環境変数経由でapksignerへ渡すため
# プロセス一覧(ps)にも露出しない。キーパスワード未登録時はstoreパスワードを流用する
KS_PASS_VAL=$(security find-generic-password -w -s platinum-hunter-ks 2>/dev/null || true)
rm -f "$APK_SIGNED"
if [ -n "$KS_PASS_VAL" ]; then
  KEY_PASS_VAL=$(security find-generic-password -w -s platinum-hunter-key 2>/dev/null || true)
  echo "▶ 署名中... (パスワードはKeychainから自動取得)"
  KS_PASS="$KS_PASS_VAL" KEY_PASS="${KEY_PASS_VAL:-$KS_PASS_VAL}" \
    "${BUILD_TOOLS_DIR}apksigner" sign --ks release.jks --ks-key-alias release \
    --ks-pass env:KS_PASS --key-pass env:KEY_PASS --out "$APK_SIGNED" "$APK_UNSIGNED"
else
  echo "▶ 署名中... (keystoreパスワード・キーパスワードの入力を求められます)"
  echo "   ※ 次回から自動化するにはスクリプト冒頭のコメントにあるKeychain登録コマンドを実行"
  "${BUILD_TOOLS_DIR}apksigner" sign --ks release.jks --ks-key-alias release --out "$APK_SIGNED" "$APK_UNSIGNED"
fi

echo "▶ 実機へインストール中..."
"$ADB" install -r "$APK_SIGNED"

echo "▶ 検証中... (端末上のAPKがいま署名したものと一致するか)"
LOCAL_SIZE=$(stat -f%z "$APK_SIGNED")
DEVICE_PATH=$("$ADB" shell pm path "$PKG" | tr -d '\r' | sed 's/^package://')
DEVICE_SIZE=$("$ADB" shell stat -c %s "$DEVICE_PATH" | tr -d '\r')
if [ "$LOCAL_SIZE" = "$DEVICE_SIZE" ]; then
  echo "✅ 完了: 端末のAPK (${DEVICE_SIZE} bytes) はいまビルド・署名したものと一致しています。"
else
  echo "❌ 検証失敗: 端末=${DEVICE_SIZE} bytes / ローカル=${LOCAL_SIZE} bytes。インストールが反映されていません。"
  exit 1
fi
