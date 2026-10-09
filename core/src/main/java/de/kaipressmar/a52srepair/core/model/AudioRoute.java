package de.kaipressmar.a52srepair.core.model;

/** Where Telecom currently plays the call, independent of the Android API that reported it. */
public enum AudioRoute {
    BLUETOOTH,
    EARPIECE,
    SPEAKER,
    WIRED_HEADSET,
    STREAMING,
    UNKNOWN
}
