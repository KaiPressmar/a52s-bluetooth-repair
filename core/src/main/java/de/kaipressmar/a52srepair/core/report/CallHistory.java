package de.kaipressmar.a52srepair.core.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only statistics over persisted call reports (newest first). */
public final class CallHistory {
    public static final int MAX_ENTRIES = 200;
    public static final long RISK_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L;

    private final List<CallReport> reports;

    public CallHistory(List<CallReport> newestFirst) {
        reports = Collections.unmodifiableList(new ArrayList<>(newestFirst));
    }

    public List<CallReport> all() {
        return reports;
    }

    public List<CallReport> bluetoothCalls() {
        List<CallReport> out = new ArrayList<>();
        for (CallReport report : reports) {
            if (report.usedBluetooth()) out.add(report);
        }
        return out;
    }

    public CallReport latestBluetoothCall() {
        for (CallReport report : reports) {
            if (report.usedBluetooth()) return report;
        }
        return null;
    }

    public int count(CallOutcome outcome, long since) {
        int n = 0;
        for (CallReport report : reports) {
            if (report.startedAt >= since && report.outcome == outcome) n++;
        }
        return n;
    }

    public int bluetoothCallCount(long since) {
        int n = 0;
        for (CallReport report : reports) {
            if (report.startedAt >= since && report.usedBluetooth()) n++;
        }
        return n;
    }

    /** True when a call in the last 7 days had a problem or needed a repair (AFTER_PROBLEMS mode). */
    public boolean riskElevated(long now) {
        for (CallReport report : reports) {
            if (now - report.startedAt > RISK_WINDOW_MS) break;
            if (report.outcome.problem || report.outcome == CallOutcome.REPAIRED) return true;
        }
        return false;
    }

    /** Prepends a report and trims to {@link #MAX_ENTRIES}. */
    public static List<CallReport> prepend(List<CallReport> newestFirst, CallReport report) {
        List<CallReport> out = new ArrayList<>(newestFirst.size() + 1);
        out.add(report);
        for (CallReport existing : newestFirst) {
            if (out.size() >= MAX_ENTRIES) break;
            out.add(existing);
        }
        return out;
    }
}
