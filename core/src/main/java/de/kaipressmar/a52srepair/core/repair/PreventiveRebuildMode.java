package de.kaipressmar.a52srepair.core.repair;

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

    public static PreventiveRebuildMode parse(String value, PreventiveRebuildMode fallback) {
        if (value == null) return fallback;
        for (PreventiveRebuildMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value)) return mode;
        }
        return fallback;
    }
}
