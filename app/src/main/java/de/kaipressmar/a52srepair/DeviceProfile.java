package de.kaipressmar.a52srepair;

import java.util.Locale;

/** Device-family detection used only for diagnostics and build-profile validation. */
final class DeviceProfile {
    enum Family {
        A52S,
        S22,
        OTHER
    }

    private DeviceProfile() {}

    static Family detect(String model, String device) {
        String normalizedModel = model == null ? "" : model.trim().toUpperCase(Locale.ROOT);
        String normalizedDevice = device == null ? "" : device.trim().toLowerCase(Locale.ROOT);

        if (normalizedModel.startsWith("SM-A528") || normalizedDevice.contains("a52sxq")) {
            return Family.A52S;
        }

        if (normalizedModel.startsWith("SM-S901")
                || normalizedDevice.equals("r0s")
                || normalizedDevice.equals("r0q")
                || normalizedDevice.startsWith("r0s")
                || normalizedDevice.startsWith("r0q")) {
            return Family.S22;
        }

        return Family.OTHER;
    }

    static boolean matchesTarget(String targetKey, String model, String device) {
        if ("a52s".equalsIgnoreCase(targetKey)) {
            return detect(model, device) == Family.A52S;
        }
        if ("s22".equalsIgnoreCase(targetKey)) {
            return detect(model, device) == Family.S22;
        }
        return false;
    }
}
