package de.kaipressmar.a52srepair.core.report;

/** End result of one call, from the user's point of view. */
public enum CallOutcome {
    /** Bluetooth call without any visible fault. */
    HEALTHY(false),
    /** A fault was detected and cleared during the call. */
    REPAIRED(false),
    /** A repairable fault persisted until the call ended. */
    UNRESOLVED(true),
    /** HFP connected, but no Bluetooth route was offered; route repair is unavailable. */
    BLUETOOTH_UNAVAILABLE(true),
    /** The call left Bluetooth by user/car choice: a strong hint that audio was not working. */
    LEFT_BLUETOOTH(true),
    /** No car or headset involved. */
    NOT_BLUETOOTH(false);

    public final boolean problem;

    CallOutcome(boolean problem) {
        this.problem = problem;
    }
}
