#!/usr/bin/env bash
# Vivid Pulse: installs the APK on the emulator, opens every page and saves screenshots.
# Fails if the app crashes or stops responding.
set -uo pipefail
APK="$1"
OUT="$2"
OLD_APK="${3:-}"   # the same app numbered build 1, for testing the in-app updater
PKG=com.jambo199.vividpulse
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

dump() { # the UI hierarchy to /tmp/ui.xml (fails while the screen keeps changing)
  local out
  out=$(adb shell uiautomator dump /sdcard/ui.xml 2>&1)
  if ! echo "$out" | grep -q "dumped to"; then
    echo "   (uiautomator: ${out//$'\n'/ })"
    return 1
  fi
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
}
shot() { # screenshot $1 after waiting $2 seconds
  sleep "${2:-2}"
  adb exec-out screencap -p > "$OUT/$1.png" && echo "📸 $1"
}
texts() { if dump; then echo "   texts: $(python3 "$HERE/ui.py" /tmp/ui.xml --texts)"; fi; }
open_page() { # the app's own page by route (as launcher shortcuts do)
  adb shell am start -W -n "$PKG/.MainActivity" --es route "$1" > /dev/null
}
tap() { # tap the element labelled $1
  for _ in 1 2 3; do
    if dump && xy=$(python3 "$HERE/ui.py" /tmp/ui.xml "$1" ${2:-}); then
      adb shell input tap $xy
      echo "👆 $1 at $xy"
      return 0
    fi
    sleep 1
  done
  echo "⚠️  could not find '$1' on screen"
  return 1
}
swipe_up() { adb shell input swipe 540 1800 540 700 350; }
swipe_down() { adb shell input swipe 540 700 540 1900 350; }

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c

if [ -n "$OLD_APK" ] && [ -f "$OLD_APK" ]; then
  echo "== In-app updater (installed as build 1; the release page has a newer build)"
  adb install -r "$OLD_APK" > /dev/null
  adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow
  adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
  sleep 2
  # The Tools page holds still, so its screen can be read while the banner shows.
  open_page tools
  shot 00a-update-available 8
  texts
  if tap "Update" exact; then
    for _ in $(seq 1 40); do
      sleep 2
      adb shell dumpsys window | grep -q "mCurrentFocus.*packageinstaller" && break
    done
    shot 00b-update-installer 2
    adb shell dumpsys window | grep -E "mCurrentFocus" | head -1
  fi
  adb logcat -d | grep -E "UpdateManager" | tail -5
  adb shell input keyevent KEYCODE_BACK
  adb shell input keyevent KEYCODE_HOME
  adb uninstall $PKG > /dev/null
fi

echo "== Installing"
adb install -r "$APK"
adb shell am start -W -n "$PKG/.MainActivity"

echo "== Live"
shot 01-live 5
shot 02-live-later 5
swipe_up; shot 03-live-scrolled 3
for page in cpu memory battery network thermal; do
  open_page "$page"; shot "04-$page" 4
done

echo "== Charging companion (a charge simulated with dumpsys battery)"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
notes() { # our notifications' titles and texts
  adb shell dumpsys notification --noredact | grep -A40 "pkg=$PKG" | grep -oE "android\.(title|text)=[^)]*\)" | head -8 | sed 's/^/   /'
}
# The emulator starts out "plugged in" at 100%: unplug it first, so the charge starts at 72%.
adb shell dumpsys battery set ac 0
adb shell dumpsys battery set status 3
adb shell dumpsys battery set level 72
sleep 2
open_page battery/companion; sleep 3
swipe_up; swipe_up; shot 05-battery-companion 2
sleep 15
adb shell dumpsys battery set ac 1
adb shell dumpsys battery set status 2
echo "-- plugged in at 72%, waiting for the charge watcher"
for level in 73 74 75 76 77 78 79 79 79 79 79 79; do
  sleep 10
  adb shell dumpsys battery set level $level
  if adb shell dumpsys notification --noredact | grep -A40 "pkg=$PKG" | grep -q "Charging"; then break; fi
done
notes
adb shell dumpsys jobscheduler | grep -E "vividpulse" | grep -iE "JOB|charging|satisf" | head -6
adb shell dumpsys battery set level 81
sleep 40
echo "-- at 81% (alert at 80%):"
notes
adb shell cmd statusbar expand-notifications; sleep 2
adb exec-out screencap -p > "$OUT/05b-charging-notifications.png" && echo "📸 05b-charging-notifications"
adb shell cmd statusbar collapse
adb shell dumpsys battery set ac 0
adb shell dumpsys battery set status 3
sleep 15
echo "-- unplugged:"
notes
adb shell dumpsys battery reset
open_page battery; sleep 2
swipe_up; swipe_up; swipe_up; shot 05c-battery-recent-charges 3
adb logcat -d | grep -E "WM-|ChargeWorker|DealsWorker" | grep -vE " [VD] " | tail -10

echo "== Device"
open_page device; shot 10-device 4
texts
swipe_up; shot 11-device-scrolled 2
swipe_up; shot 12-device-more 2
open_page display; shot 13-display 4
open_page cameras; shot 14-cameras 5
swipe_up; shot 14b-cameras-scrolled 2
open_page sensors; shot 15-sensors 3
tap "Accelerometer" && shot 16-sensor-live 3

echo "== Games"
fling_up() { for _ in $(seq 1 "$1"); do adb shell input swipe 540 1900 540 300 120; sleep 0.4; done; }
open_page games; shot 20-games 12
texts
swipe_up; shot 21-games-scrolled 2
# The whole most played chart, not just the top 20.
fling_up 14; shot 21b-games-most-played-end 4
for t in top-sellers deals upcoming free; do
  open_page "games/$t"; shot "22-games-$t" 10
done
echo "-- deals in each order, and scrolling on into more pages"
for o in players discount rated; do
  open_page "games/deals/$o"; shot "22-deals-$o" 8
done
open_page "games/deals/popular"; sleep 2
fling_up 16; shot 22-deals-more-pages 8
fling_up 10; shot 22-deals-more-pages-2 8
open_page "games/upcoming"; sleep 2
fling_up 6; shot 22-upcoming-scrolled 6
open_page "games/most-played"; sleep 1
echo "-- search and watch a game"
open_page games; sleep 1
swipe_down; swipe_down; sleep 1
if tap "Search games"; then
  sleep 1
  adb shell input text "elden"
  shot 23-games-search 5
  texts
  if tap "ELDEN RING" top; then
    sleep 6
    tap "Watch this game" && shot 24-game-watched 2
    adb shell input keyevent KEYCODE_BACK
    sleep 1
    tap "Close search"
    shot 24b-games-your-games 6
  fi
fi
open_page "game/730"; shot 25-game-detail 8
swipe_up; shot 26-game-detail-scrolled 3
open_page "game/1086940"; shot 27-game-detail-2 8
swipe_up; shot 27b-game-detail-2-prices 2
open_page "games/free"; shot 28-games-free 8

echo "== Tools"
open_page tools; shot 30-tools 3
open_page speedtest; sleep 3
tap "Start" && shot 31-speed-running 10
shot 32-speed-done 24
texts
echo "-- free up RAM"
open_page freeram; shot 37-free-ram 3
open_page freeram/start; shot 37b-free-ram-running 5
shot 37c-free-ram-done 16
adb logcat -d | grep -E "RamBooster" | tail -3
open_page fps; shot 33-smoothness 5
open_page touchtest; sleep 2
adb shell input swipe 200 800 900 1400 600
adb shell input swipe 900 700 200 1600 600
shot 34-touch 1
open_page screentest; shot 35-screen-red 2
adb shell input tap 540 1200; shot 36-screen-green 2
adb shell input keyevent KEYCODE_BACK

echo "== Settings and light theme"
open_page settings; shot 40-settings 3
swipe_up; shot 40b-settings-more 2
swipe_up; shot 40c-settings-alerts 2
swipe_up; shot 40d-settings-widgets 4
tap "Light" exact && sleep 1
open_page live; shot 41-light-live 5
open_page games; shot 42-light-games 4
open_page settings; sleep 2
tap "Dark" exact

echo "== Changelog"
open_page changelog; shot 40c-changelog 5
swipe_up; shot 40d-changelog-scrolled 2
swipe_up; swipe_up; shot 40e-changelog-oldest 2

echo "== Private DNS"
open_page dns; sleep 2
tap "Got it" exact   # Android's one-time "Viewing full screen" hint, if the screen test left it up
shot 44-dns 8
texts
echo "-- with the permission a computer grants once (adb):"
adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
open_page tools; sleep 1
open_page dns; sleep 6
if tap "Cloudflare" exact; then
  sleep 2
  echo "   private_dns: $(adb shell settings get global private_dns_mode) $(adb shell settings get global private_dns_specifier)"
  shot 44b-dns-cloudflare 2
fi
tap "Automatic" exact && sleep 2
echo "   private_dns after Automatic: $(adb shell settings get global private_dns_mode)"
adb shell settings put global private_dns_mode opportunistic

echo "== Landscape"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
open_page live; shot 43-landscape 5
adb shell settings put system user_rotation 0

echo "== Checking for crashes"
adb logcat -d > "$OUT/logcat.txt"
grep -E "Games|UpdateManager|RamBooster|vividpulse" "$OUT/logcat.txt" | grep -E " [EW] " | tail -30 || true
if grep -qE "FATAL EXCEPTION|ANR in $PKG" "$OUT/logcat.txt"; then
  echo "❌ The app crashed:"
  grep -A 40 -E "FATAL EXCEPTION|ANR in" "$OUT/logcat.txt" | head -150
  exit 1
fi
echo "✅ No crashes"
