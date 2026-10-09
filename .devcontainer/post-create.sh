#!/usr/bin/env bash
# Runs once after the dev container is created.
set -euo pipefail

# The Gradle, Claude Code and Codex volumes are created root-owned on first use.
sudo chown -R vscode:vscode /home/vscode/.gradle /home/vscode/.claude /home/vscode/.codex

# Never write local.properties: the workspace may be shared with the host. Gradle finds the SDK
# through ANDROID_HOME. If a host local.properties points to a host-only SDK path, link that
# path to the container SDK so the same file works on both sides.
if [ -f local.properties ]; then
  host_sdk="$(sed -n 's/^sdk\.dir=//p' local.properties | tail -n 1)"
  if [ -n "${host_sdk}" ] && [ ! -e "${host_sdk}" ]; then
    sudo mkdir -p "$(dirname "${host_sdk}")"
    sudo ln -s "${ANDROID_HOME}" "${host_sdk}"
    echo "Linked host SDK path ${host_sdk} -> ${ANDROID_HOME}"
  fi
fi

# Warm up the Gradle wrapper and dependencies so the first build in the IDE is fast.
./gradlew --quiet help

cat <<'MSG'

Bluetooth Call Repair dev container is ready.

  ./gradlew :core:test :app:testDebugUnitTest   # all unit tests (renders screenshots to app/build/screenshots)
  ./gradlew :app:lintDebug                      # lint
  ./gradlew :app:assembleDebug                  # debug APK in app/build/outputs/apk/debug

Real-device debugging: adb wireless debugging (adb pair / adb connect) from inside the container.
MSG
