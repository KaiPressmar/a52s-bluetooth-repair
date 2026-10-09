#!/usr/bin/env bash
# Captures Android's Bluetooth/audio/telephony state from a connected phone while a call shows
# the one-way audio fault. Run it DURING the faulty call, before rebooting the phone.
#
# WSL/remote setup: enable Developer options → Wireless debugging on the phone, then
#   adb pair <ip>:<pair-port>   (code from the phone)
#   adb connect <ip>:<port>
# Output may contain device names/MAC addresses: review before sharing publicly.
set -euo pipefail

OUT="${1:-call-audio-$(date +%Y%m%d-%H%M%S)}"
PKG=de.kaipressmar.a52srepair
mkdir -p "$OUT"

adb get-state >/dev/null
adb shell getprop ro.build.display.id > "$OUT/build.txt"
adb shell dumpsys audio > "$OUT/dumpsys-audio.txt"
adb shell dumpsys bluetooth_manager > "$OUT/dumpsys-bluetooth.txt" || true
adb shell dumpsys telecom > "$OUT/dumpsys-telecom.txt" || true
adb shell dumpsys media.audio_policy > "$OUT/audio-policy.txt" || true
adb logcat -d -b main -b system -b radio \
  | grep -iE "sco|hfp|headset|bt_|bluetooth|AudioService|AS\.|audio_hw|Telecom|a52srepair" \
  > "$OUT/logcat-filtered.txt" || true
adb exec-out run-as "$PKG" cat files/a52s-bt-repair.log > "$OUT/app-log.txt" 2>/dev/null \
  || echo "App log needs a debug build (run-as); use the in-app share instead." > "$OUT/app-log.txt"

# Most useful lines for the one-way audio question, extracted up front.
{
  echo "== communication route / mode =="
  grep -iE "mMode|communication device|mCommunicationRouteClients|ScoAudioState|mScoAudioState" "$OUT/dumpsys-audio.txt" | head -40
  echo "== voice call volume =="
  grep -iA4 "STREAM_VOICE_CALL\|STREAM_BLUETOOTH_SCO" "$OUT/dumpsys-audio.txt" | head -40
} > "$OUT/summary.txt"
echo "Captured into $OUT/ (see summary.txt)"
