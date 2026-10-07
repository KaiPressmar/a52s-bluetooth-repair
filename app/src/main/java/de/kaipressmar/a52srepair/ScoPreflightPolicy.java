package de.kaipressmar.a52srepair;

/** Eligibility rules for the A52s/Android 14 idle SCO readiness pulse. */
final class ScoPreflightPolicy {
    static final long MIN_INTERVAL_MS = 10L * 60L * 1000L;
    static final long READY_TTL_MS = 20L * 60L * 1000L;
    static final long TIMEOUT_MS = 8_000L;
    static final int MAX_ATTEMPTS_PER_CONNECTION = 2;

    private ScoPreflightPolicy() {}

    static boolean eligible(
            boolean enabled,
            String deviceProfileKey,
            int sdkInt,
            BluetoothRepair.Probe probe,
            long now,
            long lastPreflightAt,
            int attemptsThisConnection) {
        if (!enabled
                || !"a52s".equalsIgnoreCase(deviceProfileKey)
                || sdkInt != 34
                || probe == null
                || probe.health == null) {
            return false;
        }

        if (probe.health.inCommunication
                || !probe.health.hfpProfileConnected
                || !probe.health.scoAvailable
                || probe.musicActive
                || attemptsThisConnection >= MAX_ATTEMPTS_PER_CONNECTION) {
            return false;
        }

        return lastPreflightAt == 0L || now - lastPreflightAt >= MIN_INTERVAL_MS;
    }
}
