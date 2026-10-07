package de.kaipressmar.a52srepair;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compact persistent health timeline for the dashboard. */
final class HealthHistoryStore {
    static final class Entry {
        final long timestamp;
        final BluetoothHealth.State state;
        final boolean inCommunication;
        final boolean hfpConnected;
        final boolean scoAvailable;
        final boolean scoSelected;
        final String source;

        Entry(
                long timestamp,
                BluetoothHealth.State state,
                boolean inCommunication,
                boolean hfpConnected,
                boolean scoAvailable,
                boolean scoSelected,
                String source) {
            this.timestamp = timestamp;
            this.state = state;
            this.inCommunication = inCommunication;
            this.hfpConnected = hfpConnected;
            this.scoAvailable = scoAvailable;
            this.scoSelected = scoSelected;
            this.source = source == null ? "check" : source;
        }

        boolean isProblem() {
            return state == BluetoothHealth.State.SUSPECT_ROUTING
                    || state == BluetoothHealth.State.HFP_CONNECTED_NO_SCO
                    || state == BluetoothHealth.State.CALL_WITHOUT_SCO
                    || state == BluetoothHealth.State.ERROR;
        }

        boolean isHealthyCall() {
            return inCommunication && state == BluetoothHealth.State.HEALTHY;
        }
    }

    private static final String PREFS = "health-history";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 96;
    private static final long COALESCE_MS = 10L * 60L * 1000L;

    private HealthHistoryStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized void record(Context c, BluetoothHealth health, String source) {
        if (health == null) return;
        long now = System.currentTimeMillis();
        List<Entry> entries = read(c);

        if (!entries.isEmpty()) {
            Entry last = entries.get(entries.size() - 1);
            boolean same =
                    last.state == health.state
                            && last.inCommunication == health.inCommunication
                            && last.hfpConnected == health.hfpProfileConnected
                            && last.scoAvailable == health.scoAvailable
                            && last.scoSelected == health.scoSelected;
            boolean important =
                    "repair".equals(source)
                            || "manual".equals(source)
                            || "recovery".equals(source);
            if (same && !important && now - last.timestamp < COALESCE_MS) {
                return;
            }
        }

        entries.add(
                new Entry(
                        now,
                        health.state,
                        health.inCommunication,
                        health.hfpProfileConnected,
                        health.scoAvailable,
                        health.scoSelected,
                        source));

        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(entries.size() - MAX_ENTRIES, entries.size()));
        }

        prefs(c).edit().putString(KEY_ENTRIES, encode(entries)).apply();
    }

    static synchronized List<Entry> read(Context c) {
        String raw = prefs(c).getString(KEY_ENTRIES, "");
        if (raw == null || raw.isEmpty()) return new ArrayList<>();

        List<Entry> result = new ArrayList<>();
        for (String line : raw.split("\n")) {
            Entry entry = decode(line);
            if (entry != null) result.add(entry);
        }
        return result;
    }

    static synchronized void clear(Context c) {
        prefs(c).edit().remove(KEY_ENTRIES).apply();
    }

    static List<Entry> recent(List<Entry> entries, int max) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        int from = Math.max(0, entries.size() - Math.max(1, max));
        return new ArrayList<>(entries.subList(from, entries.size()));
    }

    static int problemCount(List<Entry> entries, long since) {
        int count = 0;
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry.timestamp >= since && entry.isProblem()) count++;
            }
        }
        return count;
    }

    static int healthyCallCount(List<Entry> entries, long since) {
        int count = 0;
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry.timestamp >= since && entry.isHealthyCall()) count++;
            }
        }
        return count;
    }

    static int repairCount(List<Entry> entries, long since) {
        int count = 0;
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry.timestamp >= since && "repair".equals(entry.source)) count++;
            }
        }
        return count;
    }

    private static String encode(List<Entry> entries) {
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            if (out.length() > 0) out.append('\n');
            out.append(entry.timestamp)
                    .append('|')
                    .append(entry.state.name())
                    .append('|')
                    .append(entry.inCommunication ? 1 : 0)
                    .append('|')
                    .append(entry.hfpConnected ? 1 : 0)
                    .append('|')
                    .append(entry.scoAvailable ? 1 : 0)
                    .append('|')
                    .append(entry.scoSelected ? 1 : 0)
                    .append('|')
                    .append(sanitize(entry.source));
        }
        return out.toString();
    }

    private static Entry decode(String line) {
        try {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 7) return null;
            return new Entry(
                    Long.parseLong(parts[0]),
                    BluetoothHealth.State.valueOf(parts[1]),
                    "1".equals(parts[2]),
                    "1".equals(parts[3]),
                    "1".equals(parts[4]),
                    "1".equals(parts[5]),
                    parts[6]);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String sanitize(String value) {
        if (value == null) return "check";
        return value.replace('|', '_').replace('\n', '_').replace('\r', '_');
    }
}
