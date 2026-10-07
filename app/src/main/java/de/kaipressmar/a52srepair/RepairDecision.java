package de.kaipressmar.a52srepair;

/** Pure repair/escalation state machine used by the watchdog. */
final class RepairDecision {
    enum Action {
        NONE,
        WAIT_FOR_CONFIRMATION,
        RESELECT_COMMUNICATION_ROUTE,
        ESCALATE_VENDOR_STACK
    }

    final Action action;
    final String reason;

    RepairDecision(Action action, String reason) {
        this.action = action;
        this.reason = reason;
    }

    static RepairDecision decide(
            FailureSignature signature,
            BluetoothHealth health,
            int consecutiveSuspect,
            int consecutiveDegraded,
            long nowMillis,
            long lastRepairAtMillis) {
        if (signature == null || health == null || !health.inCommunication) {
            return new RepairDecision(Action.NONE, "Kein aktiver Telefoniefehler");
        }

        if (health.speakerphoneOn) {
            return new RepairDecision(
                    Action.NONE,
                    "Lautsprecherroute wurde bewusst gewählt");
        }

        if (signature.kind == FailureSignature.Kind.RECOVERABLE_ROUTE_DRIFT) {
            if (consecutiveSuspect < 2) {
                return new RepairDecision(
                        Action.WAIT_FOR_CONFIRMATION,
                        "Routingabweichung wird kurz bestätigt");
            }
            if (nowMillis - lastRepairAtMillis < RepairPolicy.MIN_REPAIR_INTERVAL_MS) {
                return new RepairDecision(
                        Action.NONE,
                        "Reparatur-Cooldown schützt vor Routing-Schleifen");
            }
            return new RepairDecision(
                    Action.RESELECT_COMMUNICATION_ROUTE,
                    "SCO/HFP ist routbar und kann gezielt neu ausgewählt werden");
        }

        if (signature.kind == FailureSignature.Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE
                || signature.kind == FailureSignature.Kind.HFP_CONNECTED_NO_SCO) {
            if (consecutiveDegraded < 2) {
                return new RepairDecision(
                        Action.WAIT_FOR_CONFIRMATION,
                        "Fehlendes SCO wird gegen einen transienten Profilwechsel bestätigt");
            }
            return new RepairDecision(
                    Action.ESCALATE_VENDOR_STACK,
                    "HFP ist verbunden, aber Android stellt kein reparierbares SCO-Gerät bereit");
        }

        return new RepairDecision(
                Action.NONE,
                "Kein sicher automatisch reparierbares Fehlerbild");
    }

    private RepairDecision() {
        action = Action.NONE;
        reason = "";
    }
}
