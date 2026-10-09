package de.kaipressmar.a52srepair.core.device;

import java.util.Locale;

/** Device-family detection used for diagnostics and build-profile validation. */
public enum DeviceFamily {
    A52S("Galaxy A52s 5G"),
    S22("Galaxy S22"),
    OTHER(null);

    /** Marketing name, or {@code null} when the model string should be shown instead. */
    public final String displayName;

    DeviceFamily(String displayName) {
        this.displayName = displayName;
    }

    public static DeviceFamily detect(String model, String device) {
        String m = model == null ? "" : model.trim().toUpperCase(Locale.ROOT);
        String d = device == null ? "" : device.trim().toLowerCase(Locale.ROOT);
        if (m.startsWith("SM-A528") || d.contains("a52sxq")) return A52S;
        if (m.startsWith("SM-S901") || d.startsWith("r0s") || d.startsWith("r0q")) return S22;
        return OTHER;
    }
}
