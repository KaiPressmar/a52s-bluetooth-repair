package de.kaipressmar.a52srepair;

/**
 * Detects the important case where Android reports a Bluetooth communication route but the HFP
 * SCO audio transport itself is not connected.
 */
final class HfpAudioTransportPolicy {
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
            long nowMillis,
            long lastRepairAtMillis) {
        if (!isMismatch(health, hfpAudioTransportConnected)) {
            return new RepairDecision(
                    RepairDecision.Action.NONE,
                    "Kein bestätigter HFP-Audiotransportfehler");
        }

        if (consecutiveMismatch < 2) {
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

    static boolean verificationSucceeded(
            BluetoothHealth health,
            Boolean hfpAudioTransportConnected) {
        if (health == null || !health.scoSelected) return false;
        return !Boolean.FALSE.equals(hfpAudioTransportConnected);
    }
}
