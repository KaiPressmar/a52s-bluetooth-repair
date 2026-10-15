package de.kaipressmar.a52srepair.core.report;

import de.kaipressmar.a52srepair.core.diagnosis.Fault;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Compact, persisted summary of one call. */
public final class CallReport {
    private static final String SEPARATOR = ";";

    public final long startedAt;
    public final long durationMs;
    public final CallOutcome outcome;
    public final Set<Fault> faults;
    public final int routeAttempts;
    public final int volumeRestores;
    public final boolean preventiveRebuild;
    public final boolean userLeftBluetooth;
    public final int manualRepairs;
    public final int reservedRequests;
    public final int dispatchedRequests;
    public final String controlResult;
    public final String stopReason;
    public final boolean legacyEvidence;
    /** An ordinary app cannot establish actual audible cellular audio. */
    public final boolean audibleConfirmed = false;

    public CallReport(
            long startedAt,
            long durationMs,
            CallOutcome outcome,
            Set<Fault> faults,
            int routeAttempts,
            int volumeRestores,
            boolean preventiveRebuild,
            boolean userLeftBluetooth) {
        this(startedAt, durationMs, outcome, faults, routeAttempts, volumeRestores,
                preventiveRebuild, userLeftBluetooth, 0);
    }

    public CallReport(long startedAt, long durationMs, CallOutcome outcome, Set<Fault> faults,
            int routeAttempts, int volumeRestores, boolean preventiveRebuild,
            boolean userLeftBluetooth, int manualRepairs) {
        this(startedAt, durationMs, outcome, faults, routeAttempts, volumeRestores, preventiveRebuild,
                userLeftBluetooth, manualRepairs, 0, 0, "NOT_REQUESTED", "NONE", false);
    }

    public CallReport(long startedAt, long durationMs, CallOutcome outcome, Set<Fault> faults,
            int routeAttempts, int volumeRestores, boolean preventiveRebuild, boolean userLeftBluetooth,
            int manualRepairs, int reservedRequests, int dispatchedRequests, String controlResult,
            String stopReason, boolean legacyEvidence) {
        this.reservedRequests = reservedRequests;
        this.dispatchedRequests = dispatchedRequests;
        this.controlResult = safeToken(controlResult);
        this.stopReason = safeToken(stopReason);
        this.legacyEvidence = legacyEvidence;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
        this.outcome = outcome;
        this.faults = faults.isEmpty() ? EnumSet.of(Fault.NONE) : EnumSet.copyOf(faults);
        this.faults.remove(Fault.NONE);
        this.routeAttempts = routeAttempts;
        this.volumeRestores = volumeRestores;
        this.preventiveRebuild = preventiveRebuild;
        this.userLeftBluetooth = userLeftBluetooth;
        this.manualRepairs = manualRepairs;
    }

    public boolean usedBluetooth() {
        return outcome != CallOutcome.NOT_BLUETOOTH;
    }

    /** Single-line encoding, stable across versions (unknown values are skipped on decode). */
    public String encode() {
        StringBuilder faultList = new StringBuilder();
        for (Fault fault : faults) {
            if (faultList.length() > 0) faultList.append(',');
            faultList.append(fault.name());
        }
        return String.join(
                SEPARATOR,
                "2",
                Long.toString(startedAt),
                Long.toString(durationMs),
                outcome.name(),
                faultList.toString(),
                Integer.toString(routeAttempts),
                Integer.toString(volumeRestores),
                preventiveRebuild ? "1" : "0",
                userLeftBluetooth ? "1" : "0",
                Integer.toString(manualRepairs), Integer.toString(reservedRequests),
                Integer.toString(dispatchedRequests), controlResult, stopReason, legacyEvidence ? "1" : "0");
    }

    /** Returns {@code null} for malformed or unknown lines instead of throwing. */
    public static CallReport decode(String line) {
        if (line == null) return null;
        String[] p = line.split(SEPARATOR, -1);
        boolean legacy = p.length >= 9 && "1".equals(p[0]);
        if (!legacy && (p.length < 15 || !"2".equals(p[0]))) return null;
        try {
            Set<Fault> faults = EnumSet.noneOf(Fault.class);
            if (!p[4].isEmpty()) {
                for (String name : p[4].split(",")) {
                    try {
                        faults.add(Fault.valueOf(name));
                    } catch (IllegalArgumentException ignored) {
                        // Fault from a newer version.
                    }
                }
            }
            return new CallReport(
                    Long.parseLong(p[1]),
                    Long.parseLong(p[2]),
                    CallOutcome.valueOf(p[3]),
                    faults,
                    nonNegative(p[5]),
                    nonNegative(p[6]),
                    "1".equals(p[7]),
                    "1".equals(p[8]),
                    p.length > 9 ? nonNegative(p[9]) : 0,
                    legacy ? 0 : nonNegative(p[10]), legacy ? 0 : nonNegative(p[11]),
                    legacy ? "LEGACY" : safeToken(p[12]), legacy ? "UNKNOWN" : safeToken(p[13]),
                    legacy || "1".equals(p[14]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int nonNegative(String value) {
        int n = Integer.parseInt(value);
        if (n < 0) throw new IllegalArgumentException("Negative count");
        return n;
    }
    private static String safeToken(String value) {
        if (value == null || !Set.of("NONE", "UNKNOWN", "LEGACY", "NOT_REQUESTED", "ACKNOWLEDGED",
                "REQUESTED", "CANCELLED", "BLOCKED", "REJECTED", "STORAGE_FAILED", "REQUEST_BUDGET",
                "REBUILD_BUDGET", "DEADLINE", "INTERRUPTED", "USER_CHOICE", "INELIGIBLE").contains(value)) return "UNKNOWN";
        return value;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT,
                "outcome=%s faults=%s durationMs=%d routeAttempts=%d volumeRestores=%d preventive=%s userLeftBt=%s manualRepairs=%d",
                outcome,
                faults,
                durationMs,
                routeAttempts,
                volumeRestores,
                preventiveRebuild,
                userLeftBluetooth,
                manualRepairs) + " reserved=" + reservedRequests + " dispatched=" + dispatchedRequests
                + " control=" + controlResult + " stopped=" + stopReason
                + " legacyEvidence=" + legacyEvidence + " audible=UNKNOWN";
    }
}
