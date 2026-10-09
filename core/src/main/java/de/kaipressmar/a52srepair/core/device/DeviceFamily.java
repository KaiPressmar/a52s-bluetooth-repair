package de.kaipressmar.a52srepair.core.device;

import java.util.Locale;

/** Device-family detection used for diagnostics and build-profile validation. */
public enum DeviceFamily {
    A52S,
    S22,
    OTHER;

    public static DeviceFamily detect(String model, String device) {
        String m = model == null ? "" : model.trim().toUpperCase(Locale.ROOT);
        String d = device == null ? "" : device.trim().toLowerCase(Locale.ROOT);
        if (m.startsWith("SM-A528") || d.contains("a52sxq")) return A52S;
        if (m.startsWith("SM-S901") || d.startsWith("r0s") || d.startsWith("r0q")) return S22;
        return OTHER;
    }

    public static boolean matchesProfile(String profileKey, String model, String device) {
        DeviceFamily family = detect(model, device);
        return ("a52s".equalsIgnoreCase(profileKey) && family == A52S)
                || ("s22".equalsIgnoreCase(profileKey) && family == S22);
    }
}
