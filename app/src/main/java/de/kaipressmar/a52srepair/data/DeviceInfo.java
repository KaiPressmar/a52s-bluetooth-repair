package de.kaipressmar.a52srepair.data;

import android.os.Build;
import de.kaipressmar.a52srepair.core.device.DeviceFamily;
import de.kaipressmar.a52srepair.core.repair.PreventiveRebuildMode;

/** The running device and Android version, detected at runtime (one APK for all devices). */
public final class DeviceInfo {
    private DeviceInfo() {}

    public static DeviceFamily family() {
        return DeviceFamily.detect(Build.MODEL, Build.DEVICE);
    }

    public static String deviceName() {
        String name = family().displayName;
        return name != null ? name : Build.MANUFACTURER + " " + Build.MODEL;
    }

    /** e.g. "Android 14 (API 34)". */
    public static String androidVersion() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    public static PreventiveRebuildMode defaultPreventiveMode() {
        return PreventiveRebuildMode.defaultFor(family());
    }
}
