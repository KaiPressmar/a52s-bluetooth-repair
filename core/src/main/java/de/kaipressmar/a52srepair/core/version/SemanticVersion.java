package de.kaipressmar.a52srepair.core.version;

/** Minimal semantic-version helpers for release tags such as {@code v1.2.3-beta.1}. */
public final class SemanticVersion {
    private SemanticVersion() {}

    /** Strips a leading "v" and validates the format; returns {@code null} if invalid. */
    public static String normalize(String tag) {
        if (tag == null) return null;
        String v = tag.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-.][0-9A-Za-z.-]+)?") ? v : null;
    }

    /** Compares numerically; a pre-release sorts before its release. */
    public static int compare(String left, String right) {
        int[] a = numericParts(left);
        int[] b = numericParts(right);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        }
        boolean aPre = isPrerelease(left);
        boolean bPre = isPrerelease(right);
        if (aPre != bPre) return aPre ? -1 : 1;
        return left == null || right == null ? 0 : left.compareToIgnoreCase(right);
    }

    private static int[] numericParts(String version) {
        String base = version == null ? "0.0.0" : version.split("-", 2)[0];
        String[] parts = base.split("\\.");
        int[] out = {0, 0, 0};
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*$", ""));
            } catch (RuntimeException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static boolean isPrerelease(String version) {
        return version != null && version.contains("-");
    }
}
