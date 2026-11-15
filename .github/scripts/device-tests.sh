#!/usr/bin/env bash
set -euo pipefail

./gradlew assembleDebug assembleDebugAndroidTest --console=plain
shizuku_test_dir="$(mktemp -d)"
trap 'rm -rf "$shizuku_test_dir"' EXIT
curl --fail --location --retry 3 \
  https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk \
  --output "$shizuku_test_dir/shizuku.apk"
adb install -r "$shizuku_test_dir/shizuku.apk"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.briancodez.padstudio moe.shizuku.manager.permission.API_V23
unzip -p "$shizuku_test_dir/shizuku.apk" lib/x86_64/libshizuku.so > "$shizuku_test_dir/starter"
adb push "$shizuku_test_dir/starter" /data/local/tmp/padstudio-shizuku-starter
adb shell chmod 755 /data/local/tmp/padstudio-shizuku-starter
shizuku_apk_path="$(adb shell pm path moe.shizuku.privileged.api | tr -d '\r' | sed -n 's/^package://p' | head -n 1)"
adb shell /data/local/tmp/padstudio-shizuku-starter "--apk=$shizuku_apk_path"
./gradlew connectedDebugAndroidTest --console=plain
