#!/usr/bin/env bash
# Vivid Pulse on other kinds of device (the oldest Android it supports, a tablet): opens every
# page, saves a screenshot of each, and fails if the app crashes or stops responding.
set -uo pipefail
APK="$1"
OUT="$2"
PKG=com.jambo199.vividpulse
mkdir -p "$OUT"

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell settings put global hide_error_dialogs 1 || true
echo "Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r')), $(adb shell wm size | tr -d '\r'), $(adb shell wm density | tr -d '\r')"
adb logcat -c
adb install -r "$APK" || exit 1
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 8

i=1
for page in live cpu memory battery battery/companion network thermal device display cameras sensors \
  games games/deals game/730 pc tools speedtest freeram fps dns settings changelog; do
  adb shell am start -W -n "$PKG/.MainActivity" --es route "$page" > /dev/null
  sleep 5
  name=$(printf "%02d-%s" "$i" "${page//\//-}")
  adb exec-out screencap -p > "$OUT/$name.png" && echo "📸 $name"
  i=$((i + 1))
done
adb shell input swipe 500 1500 500 500 300
sleep 1
adb exec-out screencap -p > "$OUT/$(printf "%02d" "$i")-changelog-scrolled.png"

echo "== Turned sideways"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
for page in live games pc; do
  adb shell am start -W -n "$PKG/.MainActivity" --es route "$page" > /dev/null
  sleep 5
  adb exec-out screencap -p > "$OUT/90-sideways-$page.png" && echo "📸 90-sideways-$page"
done
adb shell settings put system user_rotation 0

echo "== Checking for crashes"
adb logcat -d > "$OUT/logcat.txt"
grep -E " [EW] " "$OUT/logcat.txt" | grep -iE "vividpulse|AndroidRuntime" | tail -20 || true
if grep -qE "FATAL EXCEPTION|ANR in $PKG" "$OUT/logcat.txt"; then
  echo "❌ The app crashed:"
  grep -A 40 -E "FATAL EXCEPTION|ANR in" "$OUT/logcat.txt" | head -120
  exit 1
fi
echo "✅ No crashes"
