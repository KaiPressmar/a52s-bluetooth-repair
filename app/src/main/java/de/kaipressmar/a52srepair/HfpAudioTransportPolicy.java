package de.kaipressmar.a52srepair;

/**
 * Detects the important case where Android reports a Bluetooth communication route but the HFP
 * SCO audio transport itself is not connected.
 */
final class HfpAudioTransportPolicy {
    static final long MIN_MISMATCH_CONFIRM_MS = 5_000L;

    private HfpAudioTransportPolicy() {}

    static boolean isMismatch(
            BluetoothHealth health,
            Boolean hfpAudioTransportConnected) {
        return health != null
                && health.inCommunication
                && health.hfpProfileConnected
                && health.scoAvailable
                && !health.speakerphoneOn
                && Boolean.FALSE.equals(hfpAudioTransportConnected);
    }

    static RepairDecision decide(
            BluetoothHealth health,
            Boolean hfpAudioTransportConnected,
            int consecutiveMismatch,
            long mismatchSinceMillis,
            long nowMillis,
            long lastRepairAtMillis) {
        if (!isMismatch(health, hfpAudioTransportConnected)) {
            return new RepairDecision(
                    RepairDecision.Action.NONE,
                    "Kein bestätigter HFP-Audiotransportfehler");
        }

        boolean confirmedLongEnough =
                mismatchSinceMillis > 0L
                        && nowMillis - mismatchSinceMillis >= MIN_MISMATCH_CONFIRM_MS;
        if (consecutiveMismatch < 2 || !confirmedLongEnough) {
            return new RepairDecision(
                    RepairDecision.Action.WAIT_FOR_CONFIRMATION,
                    "HFP-Audiotransport ist noch nicht verbunden; Zustand wird kurz bestätigt");
        }

        if (nowMillis - lastRepairAtMillis < RepairPolicy.MIN_REPAIR_INTERVAL_MS) {
            return new RepairDecision(
                    RepairDecision.Action.NONE,
                    "Verifizierte Reparatur liegt noch im kurzen Cooldown");
        }

        return new RepairDecision(
                RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                "HFP ist verbunden, aber der SCO-Audiotransport selbst ist nicht aktiv");
    }

    static long confirmationDelayMillis(long mismatchSinceMillis, long nowMillis) {
        if (mismatchSinceMillis <= 0L) return MIN_MISMATCH_CONFIRM_MS;
        long remaining =
                MIN_MISMATCH_CONFIRM_MS - Math.max(0L, nowMillis - mismatchSinceMillis);
        return Math.max(500L, remaining);
    }

    static boolean verificationSucceeded(
            BluetoothHealth health,
            Boolean hfpAudioTransportConnected) {
        return health != null
                && health.scoSelected
                && Boolean.TRUE.equals(hfpAudioTransportConnected);
    }
}
