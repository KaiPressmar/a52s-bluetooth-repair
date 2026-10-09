#!/usr/bin/env bash
# Installs the CI toolchain (JDK 21, Gradle 8.13, Android SDK 34-36) into user space; no sudo.
# Usage: scripts/setup-dev-env.sh [install-dir]   then: source <install-dir>/env.sh
set -euo pipefail

DEST="${1:-$HOME/dev-tools}"
GRADLE_VERSION=8.13
CMDLINE_TOOLS=commandlinetools-linux-13114758_latest.zip
mkdir -p "$DEST"
cd "$DEST"

unzip_py() { python3 -c "import sys,zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$1" "$2"; }

if ! ls -d jdk-21* >/dev/null 2>&1; then
  echo "Installing Temurin JDK 21 …"
  curl -fsSL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar xzf jdk21.tar.gz && rm jdk21.tar.gz
fi
JDK_DIR="$DEST/$(ls -d jdk-21* | head -1)"

if [ ! -x "gradle-$GRADLE_VERSION/bin/gradle" ]; then
  echo "Installing Gradle $GRADLE_VERSION …"
  curl -fsSL -o gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  unzip_py gradle.zip . && rm gradle.zip && chmod +x "gradle-$GRADLE_VERSION/bin/gradle"
fi

if [ ! -x android-sdk/cmdline-tools/latest/bin/sdkmanager ]; then
  echo "Installing Android command-line tools …"
  curl -fsSL -o clt.zip "https://dl.google.com/android/repository/$CMDLINE_TOOLS"
  mkdir -p android-sdk/cmdline-tools
  unzip_py clt.zip android-sdk/cmdline-tools && rm clt.zip
  mv android-sdk/cmdline-tools/cmdline-tools android-sdk/cmdline-tools/latest
  chmod +x android-sdk/cmdline-tools/latest/bin/*
fi

cat > env.sh <<ENV
export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="$DEST/android-sdk"
export ANDROID_SDK_ROOT="\$ANDROID_HOME"
export PATH="\$JAVA_HOME/bin:$DEST/gradle-$GRADLE_VERSION/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
ENV
# shellcheck disable=SC1091
source env.sh

yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-34" "platforms;android-35" "platforms;android-36" "build-tools;35.0.0"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "sdk.dir=$ANDROID_HOME" > "$REPO_DIR/local.properties"
echo "Done. Run: source $DEST/env.sh"
