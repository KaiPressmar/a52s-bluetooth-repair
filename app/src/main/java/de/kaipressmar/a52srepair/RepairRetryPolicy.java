package de.kaipressmar.a52srepair;

/**
 * Bounded retry policy for automatic route repair.
 *
 * A failed asynchronous route request may be transient while HFP/SCO is settling, but repeated
 * route forcing can cause audio thrashing. The watchdog therefore allows at most two automatic
 * attempts per communication incident.
 */
final class RepairRetryPolicy {
    static final int MAX_ATTEMPTS_PER_INCIDENT = 2;
    static final long RETRY_AFTER_REJECT_MS = 5_000L;
    static final long RETRY_AFTER_VERIFY_FAILURE_MS = 2_500L;

    private RepairRetryPolicy() {}

    static boolean canAttempt(
            boolean autoRepairEnabled,
            RepairDecision decision,
            int attemptsInIncident) {
        return autoRepairEnabled
                && decision != null
                && decision.isRepair()
                && attemptsInIncident < MAX_ATTEMPTS_PER_INCIDENT;
    }

    static boolean shouldRetryAfterVerification(
            BluetoothHealth health,
            boolean routeOwned,
            int attemptsInIncident) {
        return health != null
                && health.inCommunication
                && !health.speakerphoneOn
                && !health.scoSelected
                && health.scoAvailable
                && routeOwned
                && attemptsInIncident < MAX_ATTEMPTS_PER_INCIDENT;
    }

    static boolean exhausted(int attemptsInIncident) {
        return attemptsInIncident >= MAX_ATTEMPTS_PER_INCIDENT;
    }
}
