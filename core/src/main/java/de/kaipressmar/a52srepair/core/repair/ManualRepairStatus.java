package de.kaipressmar.a52srepair.core.repair;

/** A manual request is explicit, but still respects call lifecycle and bounded retries. */
public enum ManualRepairStatus {
    STARTED, NO_ACTIVE_CALL, BLUETOOTH_UNAVAILABLE, ROUTE_UNKNOWN,
    MULTIPLE_CALLS, ALTERNATIVE_ROUTE, BUSY, COOLDOWN, BUDGET_EXHAUSTED, COMMAND_FAILED, AMBIGUOUS_DEVICE
}
