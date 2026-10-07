package de.kaipressmar.a52srepair;

/**
 * Battery-conscious watchdog timing.
 *
 * The known failure usually matters when a communication session starts, so the service is
 * primarily event-driven. These timers are only safety nets for missed vendor callbacks.
 */
final class WatchdogSchedule {
    static final long IDLE_HEARTBEAT_MS = 30L * 60L * 1000L;
    static final long ACTIVE_HEALTHY_RECHECK_MS = 2L * 60L * 1000L;
    static final long SUSPECT_CONFIRM_MS = 4_000L;
    static final long DEGRADED_RECHECK_MS = 5L * 60L * 1000L;

    private WatchdogSchedule() {}

    static long nextDelayMillis(
            BluetoothHealth health,
            int consecutiveSuspect,
            int consecutiveDegraded) {
        if (health == null || !health.inCommunication) {
            return IDLE_HEARTBEAT_MS;
        }

        if (health.needsRepair()) {
            return consecutiveSuspect < 2 ? SUSPECT_CONFIRM_MS : DEGRADED_RECHECK_MS;
        }

        if (health.state == BluetoothHealth.State.HFP_CONNECTED_NO_SCO
                || health.state == BluetoothHealth.State.CALL_WITHOUT_SCO) {
            return consecutiveDegraded < 2 ? 8_000L : DEGRADED_RECHECK_MS;
        }

        if (health.state == BluetoothHealth.State.ERROR) {
            return DEGRADED_RECHECK_MS;
        }

        return ACTIVE_HEALTHY_RECHECK_MS;
    }
}
