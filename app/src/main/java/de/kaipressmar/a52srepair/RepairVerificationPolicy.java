package de.kaipressmar.a52srepair;

/** Timing and stop conditions for verifying Android's asynchronous route selection. */
final class RepairVerificationPolicy {
    static final long FIRST_VERIFY_MS = 8_000L;
    static final long FINAL_GRACE_MS = 22_000L;
    static final long MAX_ROUTE_SETTLE_MS = FIRST_VERIFY_MS + FINAL_GRACE_MS;

    private RepairVerificationPolicy() {}

    static boolean needsFinalVerification(
            boolean scoSelected,
            boolean inCommunication,
            boolean routeOwned,
            boolean alreadyFinal) {
        return !scoSelected && inCommunication && routeOwned && !alreadyFinal;
    }
}
