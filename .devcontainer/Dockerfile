# Android build environment matching CI: JDK 21, Android SDK platform 37, build-tools 36.
FROM mcr.microsoft.com/devcontainers/java:3-25-bookworm

ARG CMDLINE_TOOLS_VERSION=13114758
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# The SDK is part of the image, so container (re)creation and Codespaces prebuilds stay fast.
# The base image ships a Yarn apt source whose signing key has expired; Yarn isn't needed here.
RUN rm -f /etc/apt/sources.list.d/yarn.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip \
    && (yes | sdkmanager --licenses > /dev/null) \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-37.0" \
        "build-tools;36.0.0" \
    && chown -R vscode:vscode "${ANDROID_HOME}"
