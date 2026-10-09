package de.kaipressmar.a52srepair.core.repair;

import de.kaipressmar.a52srepair.core.device.DeviceFamily;

/**
 * When to rebuild Bluetooth call audio once at call start, without any detected fault.
 *
 * A silent downlink inside the vendor audio HAL is invisible to apps. While driving, the user
 * can only take the call through the car, so a preventive rebuild is the hands-free remedy.
 */
public enum PreventiveRebuildMode {
    ALWAYS,
    AFTER_PROBLEMS,
    OFF;

    /**
     * A52s: the silent downlink is undetectable, so rebuild on every Bluetooth call. Other
     * devices: rebuild only after a problem was seen in the last 7 days.
     */
    public static PreventiveRebuildMode defaultFor(DeviceFamily family) {
        return family == DeviceFamily.A52S ? ALWAYS : AFTER_PROBLEMS;
    }

    public static PreventiveRebuildMode parse(String value, PreventiveRebuildMode fallback) {
        if (value == null) return fallback;
        for (PreventiveRebuildMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value)) return mode;
        }
        return fallback;
    }
}
