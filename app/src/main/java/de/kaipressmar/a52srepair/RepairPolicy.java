package de.kaipressmar.a52srepair;

/** Pure decision logic for the opt-in watchdog. Contains no Android framework calls. */
final class RepairPolicy {
    static final long MIN_REPAIR_INTERVAL_MS = 15L * 60L * 1000L;

    private RepairPolicy() {}

    static boolean canAutoRepair(
            BluetoothHealth health,
            int consecutiveSuspect,
            long nowMillis,
            long lastRepairAtMillis) {
        if (health == null
                || !health.needsRepair()
                || health.speakerphoneOn
                || consecutiveSuspect < 2) {
            return false;
        }
        return nowMillis - lastRepairAtMillis >= MIN_REPAIR_INTERVAL_MS;
    }
}
