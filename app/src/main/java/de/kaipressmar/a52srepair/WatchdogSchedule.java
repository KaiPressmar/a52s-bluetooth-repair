package de.kaipressmar.a52srepair;

/**
 * Battery-conscious watchdog timing.
 *
 * Bluetooth state/profile events are the primary triggers. Periodic checks are only a safety net
 * while Bluetooth is enabled. With Bluetooth off there is deliberately no polling.
 */
final class WatchdogSchedule {
    static final long NO_RECHECK_MS = -1L;
    static final long BLUETOOTH_ON_NO_HFP_RECHECK_MS = 30L * 60L * 1000L;
    static final long HFP_CONNECTED_IDLE_RECHECK_MS = 15L * 60L * 1000L;
    static final long ACTIVE_HEALTHY_RECHECK_MS = 2L * 60L * 1000L;
    static final long SUSPECT_CONFIRM_MS = 4_000L;
    static final long MISSING_SCO_CONFIRM_MS = 8_000L;
    static final long DEGRADED_RECHECK_MS = 5L * 60L * 1000L;

    private WatchdogSchedule() {}

    static long nextDelayMillis(
            BluetoothHealth health,
            int consecutiveSuspect,
            int consecutiveDegraded) {
        if (health == null) {
            return DEGRADED_RECHECK_MS;
        }

        if (health.state == BluetoothHealth.State.BLUETOOTH_OFF) {
            return NO_RECHECK_MS;
        }

        if (health.state == BluetoothHealth.State.PERMISSION_REQUIRED) {
            return NO_RECHECK_MS;
        }

        if (!health.inCommunication) {
            return health.hfpProfileConnected
                    ? HFP_CONNECTED_IDLE_RECHECK_MS
                    : BLUETOOTH_ON_NO_HFP_RECHECK_MS;
        }

        if (health.needsRepair()) {
            return consecutiveSuspect < 2 ? SUSPECT_CONFIRM_MS : DEGRADED_RECHECK_MS;
        }

        if (health.state == BluetoothHealth.State.HFP_CONNECTED_NO_SCO
                || health.state == BluetoothHealth.State.CALL_WITHOUT_SCO) {
            return consecutiveDegraded < 2
                    ? MISSING_SCO_CONFIRM_MS
                    : DEGRADED_RECHECK_MS;
        }

        if (health.state == BluetoothHealth.State.ERROR) {
            return DEGRADED_RECHECK_MS;
        }

        return ACTIVE_HEALTHY_RECHECK_MS;
    }
}
