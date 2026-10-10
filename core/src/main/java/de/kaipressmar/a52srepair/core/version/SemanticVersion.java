package de.kaipressmar.a52srepair.core.version;

import java.math.BigInteger;

/** Semantic versions; numeric components never overflow and build metadata does not affect order. */
public final class SemanticVersion {
    private SemanticVersion() {}

    public static String normalize(String tag) {
        if (tag == null) return null;
        String v = tag.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        if (!v.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?")) return null;
        String base = v.split("\\+", 2)[0];
        if (base.contains("-")) {
            for (String id : base.split("-", 2)[1].split("\\.")) {
                if (id.matches("[0-9]+") && id.length() > 1 && id.startsWith("0")) return null;
            }
        }
        return v;
    }

    /** Invalid versions sort before valid versions; two invalid values compare equal. */
    public static int compare(String left, String right) {
        String a = normalize(left), b = normalize(right);
        if (a == null || b == null) return a == b ? 0 : a == null ? -1 : 1;
        String[] aa = a.split("\\+", 2)[0].split("-", 2);
        String[] bb = b.split("\\+", 2)[0].split("-", 2);
        String[] an = aa[0].split("\\."), bn = bb[0].split("\\.");
        for (int i = 0; i < 3; i++) {
            int c = new BigInteger(an[i]).compareTo(new BigInteger(bn[i]));
            if (c != 0) return c;
        }
        if (aa.length != bb.length) return aa.length == 1 ? 1 : -1;
        if (aa.length == 1) return 0;
        String[] ap = aa[1].split("\\."), bp = bb[1].split("\\.");
        for (int i = 0; i < Math.min(ap.length, bp.length); i++) {
            boolean numericA = ap[i].matches("[0-9]+"), numericB = bp[i].matches("[0-9]+");
            int c = numericA && numericB ? new BigInteger(ap[i]).compareTo(new BigInteger(bp[i]))
                    : numericA != numericB ? (numericA ? -1 : 1) : ap[i].compareTo(bp[i]);
            if (c != 0) return c;
        }
        return Integer.compare(ap.length, bp.length);
    }
}
