#!/usr/bin/env bash
# Measures how Photo Dokan runs on a real phone, e.g. the studio's.
#
#   1. On the phone: Settings → About phone → tap "Build number" 7 times, then
#      Settings → Developer options → turn on USB debugging. Connect it by USB and allow this computer.
#   2. Run:  scripts/phone-check.sh            (add --no-install to keep the app already on the phone)
#   3. Use the app on the phone as the operator would when asked, then press Enter here.
#
# It installs the "profile" build: release speed, so the numbers are real, but signed like a debug build.
# The report is printed and saved under build/phone-check/.
set -euo pipefail

cd "$(dirname "$0")/.."
ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
# adb reads standard input, which would swallow the Enter typed at the prompt below.
adb() { "$ADB" "$@" </dev/null; }
PACKAGE=com.tlw.photodokan

devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [ "$(echo "$devices" | grep -c .)" -ne 1 ]; then
  echo "Connect exactly one phone with USB debugging on (found: ${devices:-none})." >&2
  exit 1
fi

prop() { adb shell getprop "$1" | tr -d '\r'; }
model="$(prop ro.product.manufacturer) $(prop ro.product.model)"
ram_mb=$(( $(adb shell cat /proc/meminfo | awk '/MemTotal/ { print $2 }') / 1024 ))

if [ "${1:-}" != "--no-install" ]; then
  echo "Installing the profile build…"
  ./gradlew -q :app-android:installProfile
fi

adb logcat -c
adb shell am start -n "$PACKAGE/.android.MainActivity" >/dev/null 2>&1
sleep 3
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null

cat <<'EOF'

On the phone, for about a minute:
  • Take a photo (or choose one), then drag and pinch it in the crop frame.
  • Open Adjust and move every slider back and forth.
  • Save it, open another saved customer, and add a few customers to print.
  • On the Print tab press Share PDF (you can close the share screen).

Press Enter here when done.
EOF
read -r

report_dir=build/phone-check
mkdir -p "$report_dir"
report="$report_dir/$(echo "$model" | tr ' /' '__')-$(date +%Y%m%d-%H%M).txt"
{
  echo "Phone:    $model"
  echo "Android:  $(prop ro.build.version.release) (API $(prop ro.build.version.sdk))"
  echo "Chip:     $(prop ro.soc.model) $(prop ro.board.platform)"
  echo "RAM:      $ram_mb MB"
  echo "Screen:   $(adb shell wm size | tr -d '\r' | awk '{ print $NF }')"
  echo
  echo "== Smoothness while using the app (16 ms per frame is smooth on a 60 Hz screen) =="
  adb shell dumpsys gfxinfo "$PACKAGE" | tr -d '\r' |
    grep -E 'Total frames rendered|Janky frames:|^50th percentile|^90th percentile|^99th percentile|Slow UI thread|Slow bitmap uploads|Slow issue draw' || true
  echo
  echo "== Slow steps (open, save, print) =="
  adb logcat -d -s PhotoDokanPerf | tr -d '\r' | { grep PhotoDokanPerf || echo "(none: nothing was opened, saved or printed)"; } | sed 's/.*PhotoDokanPerf: //'
} | tee "$report"

cat <<EOF

Saved to $report
Smooth enough: the 90th percentile at or under about 32 ms and fewer than 10% janky frames.
EOF
