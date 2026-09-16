# A52s Bluetooth Repair

Experimental diagnostic app for the Samsung Galaxy A52s 5G Bluetooth call-audio/HFP/SCO problem.

## What v0.3 does

- Logs Bluetooth/audio snapshots every 15 seconds while monitoring is enabled.
- Shows the active Android communication audio device and output devices.
- **Test 1** clears Android's communication-device routing, disables speaker routing and returns AudioManager to normal mode.
- **Test 2** stops legacy SCO, clears the communication device, switches to communication mode, then attempts to select a Bluetooth SCO communication device; if none is selectable it falls back to `startBluetoothSco()`.
- Shares the collected text log through Android's share sheet.

No root, Shizuku or system modification is used in this version. These tests only use public Android APIs and therefore may not reset a fault inside Samsung/Qualcomm proprietary services.

## Suggested test

When the car-call problem is present, **do not reboot**. Open the app, take a snapshot, start monitoring, try Test 1 and make a call. If still broken, try Test 2 and make another call. Then share the log for comparison.

## APK

GitHub Actions builds a debug APK on every push to `main`. Open the latest `Build Android APK` run and download the `a52s-bluetooth-repair-debug` artifact.

## Warning

Experimental software. The repair buttons temporarily change Android audio routing and may interrupt active audio. Do not operate the app while driving.
